package cloud.leqwiss.aospfluid

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import android.view.SurfaceControl
import android.view.animation.Animation
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class FluidAnimationsModule : XposedModule() {

    @Volatile
    private var settings = AnimationSettings.DEFAULT

    private val fields = ConcurrentHashMap<String, Field?>()
    private val methods = ConcurrentHashMap<String, Method?>()

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName !in HOOK_PACKAGES) return

        val loadedPreferences = runCatching {
            getRemotePreferences(PREFERENCES_GROUP)
        }.getOrNull()

        if (loadedPreferences != null) {
            settings = AnimationSettings.read(loadedPreferences)
            registerPreferenceListener(loadedPreferences)
        } else {
            log(Log.WARN, TAG, "Remote preferences unavailable; using OriginOS defaults")
        }

        val loader = param.defaultClassLoader
        runCatching { hookRemoteHandlers(loader) }
            .onFailure { log(Log.WARN, TAG, "Remote transition bypass hook failed", it) }
        runCatching { hookShellLoadAnimation(loader) }
            .onFailure { log(Log.WARN, TAG, "Shell loadAnimation hook failed", it) }
        runCatching { hookSurfaceAnimatorCancel(loader) }
            .onFailure { log(Log.WARN, TAG, "Surface cancel hook failed", it) }
        runCatching { hookShellAbort(loader) }
            .onFailure { log(Log.WARN, TAG, "Shell abort hook failed", it) }

        log(
            Log.INFO,
            TAG,
            "Shell transition hooks installed: process=${param.packageName}, " +
                "profile=${settings.profile.key}"
        )
    }

    private fun findClass(name: String, loader: ClassLoader?): Class<*>? {
        return runCatching {
            Class.forName(name, false, loader ?: FluidAnimationsModule::class.java.classLoader)
        }.getOrNull()
    }

    private fun registerPreferenceListener(preferences: SharedPreferences) {
        runCatching {
            preferences.registerOnSharedPreferenceChangeListener { _, _ ->
                settings = AnimationSettings.read(preferences)
                log(
                    Log.INFO,
                    TAG,
                    "Settings updated: profile=${settings.profile.key}, speed=${settings.speed}"
                )
            }
        }.onFailure {
            log(Log.WARN, TAG, "Preference listener registration failed", it)
        }
    }

    private fun hookRemoteHandlers(loader: ClassLoader?) {
        for (className in REMOTE_HANDLERS) {
            val handlerClass = findClass(className, loader) ?: continue
            val method = handlerClass.declaredMethods.firstOrNull {
                it.name == "startAnimation" && it.parameterCount == 5
            } ?: continue

            hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    try {
                        val info = chain.getArg(1)
                        val type = readInt(info, "getType")
                        if (settings.enabled && type in APP_TRANSITIONS) {
                            removeRequestedRemote(chain.thisObject, chain.getArg(0))
                            return@intercept false
                        }
                        return@intercept chain.proceed() == true
                    } catch (throwable: Throwable) {
                        log(Log.WARN, TAG, "Remote transition bypass failed", throwable)
                        return@intercept chain.proceed() == true
                    }
                }
        }
    }

    private fun hookShellLoadAnimation(loader: ClassLoader?) {
        val handlerClass = findClass(DEFAULT_TRANSITION_HANDLER, loader) ?: return
        val method = handlerClass.getDeclaredMethod(
            "loadAnimation",
            Int::class.javaPrimitiveType,
            findClass(TRANSITION_INFO, loader),
            findClass(TRANSITION_INFO_CHANGE, loader),
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                var original: Animation? = null
                try {
                    original = chain.proceed() as? Animation ?: return@intercept null
                    val info = chain.getArg(1)
                    val change = chain.getArg(2)
                    val type = chain.getArg(0) as? Int ?: return@intercept original
                    val current = settings
                    if (!current.enabled || type !in APP_TRANSITIONS || !isFluidChange(change)) {
                        return@intercept original
                    }
                    val enter = isOpeningMode(readInt(change, "getMode"))
                    return@intercept FluidAnimation(current.profile, enter, current.speed)
                } catch (throwable: Throwable) {
                    log(Log.WARN, TAG, "Fluid animation replacement failed", throwable)
                    return@intercept original
                }
            }
    }

    private fun hookSurfaceAnimatorCancel(loader: ClassLoader?) {
        val animatorClass = findClass(DEFAULT_SURFACE_ANIMATOR, loader) ?: return
        val method = animatorClass.declaredMethods.firstOrNull {
            it.name == "buildSurfaceAnimation" && it.parameterCount >= 6 &&
                Animation::class.java.isAssignableFrom(it.parameterTypes[1]) &&
                SurfaceControl::class.java.isAssignableFrom(it.parameterTypes[2])
        } ?: return

        hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val animations = chain.getArg(0) as? MutableList<Animator>
                val leash = chain.getArg(2) as? SurfaceControl
                val beforeCount = animations?.size ?: 0
                val result = chain.proceed()
                try {
                    if (settings.enabled && settings.cancelFix && animations != null && leash != null) {
                        for (index in beforeCount until animations.size) {
                            animations.getOrNull(index)?.let { animator ->
                                animator.addListener(object : AnimatorListenerAdapter() {
                                    private var handled = false

                                    override fun onAnimationCancel(animation: Animator) {
                                        if (handled) return
                                        handled = true
                                        collapseSurface(leash)
                                    }
                                })
                            }
                        }
                    }
                } catch (throwable: Throwable) {
                    log(Log.WARN, TAG, "Cancel guard registration failed", throwable)
                }
                result
            }
    }

    private fun hookShellAbort(loader: ClassLoader?) {
        val transitionsClass = findClass(SHELL_TRANSITIONS, loader) ?: return
        val abortMethod = transitionsClass.declaredMethods.firstOrNull {
            it.name == "onAbort" && it.parameterCount == 1 &&
                it.parameterTypes[0].name == ACTIVE_TRANSITION
        } ?: return
        hook(abortMethod)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val active = chain.getArg(0)
                try {
                    if (settings.enabled && settings.cancelFix) {
                        collapseOpeningSurfaces(active)
                    }
                } catch (throwable: Throwable) {
                    log(Log.WARN, TAG, "Cancel fix failed", throwable)
                }
                chain.proceed()
            }
    }

    private fun removeRequestedRemote(handler: Any?, transition: Any?) {
        if (handler == null || transition == null) return
        val requested = readField(handler, "mRequestedRemotes") as? MutableMap<*, *> ?: return
        runCatching { requested.remove(transition) }
    }

    private fun collapseOpeningSurfaces(active: Any?) {
        if (active == null || Looper.getMainLooper().thread != Thread.currentThread()) return
        val info = readField(active, "mInfo") ?: return
        val transaction = readField(active, "mStartT") as? SurfaceControl.Transaction
            ?: readField(active, "mFinishT") as? SurfaceControl.Transaction
            ?: return
        val changes = runCatching {
            info.javaClass.getMethod("getChanges").invoke(info) as? List<*>
        }.getOrNull() ?: return

        for (change in changes) {
            if (change == null) continue
            val mode = readInt(change, "getMode")
            if (!isOpeningMode(mode)) continue
            val leash = runCatching {
                change.javaClass.getMethod("getLeash").invoke(change)
            }.getOrNull() as? SurfaceControl ?: continue
            if (leash.isValid) collapseSurface(transaction, leash)
        }
        transaction.apply()
    }

    private fun collapseSurface(leash: SurfaceControl) {
        if (!leash.isValid) return
        collapseSurface(SurfaceControl.Transaction(), leash)
    }

    private fun collapseSurface(transaction: SurfaceControl.Transaction, leash: SurfaceControl) {
        if (!leash.isValid) return
        setSurfaceMatrix(transaction, leash, 0.10f)
        transaction.setAlpha(leash, 0f)
        transaction.apply()
    }

    private fun setSurfaceMatrix(
        transaction: SurfaceControl.Transaction,
        leash: SurfaceControl,
        scale: Float
    ) {
        val method = methods.getOrPut(transaction.javaClass.name + ":setMatrix") {
            runCatching {
                transaction.javaClass.getMethod(
                    "setMatrix",
                    SurfaceControl::class.java,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                )
            }.getOrNull()
        }
        method?.invoke(transaction, leash, scale, 0f, 0f, scale)
    }

    private fun isOpeningMode(mode: Int): Boolean {
        return mode == 1 || mode == 3
    }

    private fun isFluidChange(change: Any?): Boolean {
        if (change == null) return false
        val mode = readInt(change, "getMode")
        if (mode !in APP_TRANSITIONS) return false
        val taskInfo = runCatching {
            change.javaClass.getMethod("getTaskInfo").invoke(change)
        }.getOrNull()
        if (taskInfo == null) return true
        val activityType = readInt(taskInfo, "getActivityType")
        return activityType != 2
    }

    private fun readInt(target: Any?, methodName: String): Int {
        if (target == null) return Int.MIN_VALUE
        val method = methods.getOrPut(target.javaClass.name + ":" + methodName) {
            runCatching {
                target.javaClass.getMethod(methodName)
            }.getOrNull()
        }
        return runCatching { method?.invoke(target) as? Int }.getOrNull() ?: Int.MIN_VALUE
    }

    private fun readField(target: Any?, name: String): Any? {
        if (target == null) return null
        val field = fields.getOrPut(target.javaClass.name + ":" + name) {
            runCatching {
                var currentClass: Class<*>? = target.javaClass
                while (currentClass != null) {
                    val declared = runCatching {
                        currentClass.getDeclaredField(name).apply { isAccessible = true }
                    }.getOrNull()
                    if (declared != null) return@runCatching declared
                    currentClass = currentClass.superclass
                }
                null
            }.getOrNull()
        }
        return runCatching { field?.get(target) }.getOrNull()
    }

    companion object {
        private const val TAG = "AOSPFluidAnimations"
        private const val PREFERENCES_GROUP = "fluid_animations"
        private const val DEFAULT_TRANSITION_HANDLER =
            "com.android.wm.shell.transition.DefaultTransitionHandler"
        private const val DEFAULT_SURFACE_ANIMATOR =
            "com.android.wm.shell.transition.DefaultSurfaceAnimator"
        private const val SHELL_TRANSITIONS = "com.android.wm.shell.transition.Transitions"
        private const val ACTIVE_TRANSITION =
            "com.android.wm.shell.transition.Transitions\$ActiveTransition"
        private const val TRANSITION_INFO = "android.window.TransitionInfo"
        private const val TRANSITION_INFO_CHANGE = "android.window.TransitionInfo\$Change"
        private val REMOTE_HANDLERS = setOf(
            "com.android.wm.shell.transition.RemoteTransitionHandler",
            "com.android.wm.shell.transition.OneShotRemoteHandler"
        )
        private val APP_TRANSITIONS = setOf(1, 2, 3, 4)
        private val HOOK_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3"
        )
    }
}
