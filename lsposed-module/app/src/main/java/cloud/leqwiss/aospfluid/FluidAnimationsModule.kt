package cloud.leqwiss.aospfluid

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceControl
import android.view.WindowManager
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

    @Volatile
    private var blurDisabledByFailure = false

    private val fields = ConcurrentHashMap<String, Field?>()
    private val methods = ConcurrentHashMap<String, Method?>()

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != SYSTEM_PACKAGE) return

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
        runCatching { hookLoadAnimation(loader) }
            .onFailure { log(Log.WARN, TAG, "loadAnimation hook failed", it) }
        runCatching { hookWindowAnimationSpec(loader) }
            .onFailure { log(Log.WARN, TAG, "WindowAnimationSpec hook failed", it) }
        runCatching { hookCancel(loader) }
            .onFailure { log(Log.WARN, TAG, "Cancel hook failed", it) }
        runCatching { hookZeroLatency(loader) }
            .onFailure { log(Log.WARN, TAG, "Zero latency hook failed", it) }
        log(Log.INFO, TAG, "Fluid animations hooked; profile=${settings.profile.key}")
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
                log(Log.INFO, TAG, "Profile updated: ${settings.profile.key}, speed=${settings.speed}")
            }
        }.onFailure {
            log(Log.WARN, TAG, "Preference listener registration failed", it)
        }
    }

    private fun hookLoadAnimation(loader: ClassLoader?) {
        val containerClass = findClass(WINDOW_CONTAINER, loader) ?: return
        val method = containerClass.getDeclaredMethod(
            "loadAnimation",
            WindowManager.LayoutParams::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                try {
                    val original = chain.proceed() as? Animation ?: return@intercept null
                    val transit = chain.getArg(1) as? Int ?: return@intercept original
                    val enter = chain.getArg(2) as? Boolean ?: return@intercept original
                    val current = settings
                    if (!current.enabled || !isAppTransition(transit)) {
                        return@intercept original
                    }
                    if (!isFluidTarget(chain.thisObject)) return@intercept original
                    return@intercept FluidAnimation(current.profile, enter, current.speed)
                } catch (throwable: Throwable) {
                    log(Log.WARN, TAG, "Fluid animation replacement failed", throwable)
                    return@intercept null
                }
            }
    }

    private fun hookWindowAnimationSpec(loader: ClassLoader?) {
        val specClass = findClass(WINDOW_ANIMATION_SPEC, loader) ?: return
        val method = specClass.getDeclaredMethod(
            "apply",
            SurfaceControl.Transaction::class.java,
            SurfaceControl::class.java,
            Long::class.javaPrimitiveType
        )
        hook(method)
            .setPriority(XposedInterface.PRIORITY_DEFAULT)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                try {
                    val animation = readField(chain.thisObject, "mAnimation") as? FluidAnimation
                    val current = settings
                    if (animation != null && animation.enter &&
                        animation.profile == AnimationProfile.COLOROS &&
                        current.colorBlur && !blurDisabledByFailure && current.blurRadius > 0
                    ) {
                        val transaction = chain.getArg(0) as? SurfaceControl.Transaction
                        val leash = chain.getArg(1) as? SurfaceControl
                        if (transaction != null && leash != null && leash.isValid) {
                            val blurMethod = methods.getOrPut("setBackgroundBlurRadius") {
                                runCatching {
                                    transaction.javaClass.getMethod(
                                        "setBackgroundBlurRadius",
                                        SurfaceControl::class.java,
                                        Int::class.javaPrimitiveType
                                    )
                                }.getOrNull()
                            }
                            blurMethod?.invoke(transaction, leash, current.blurRadius)
                        }
                    }
                } catch (throwable: Throwable) {
                    blurDisabledByFailure = true
                    log(Log.WARN, TAG, "ColorOS blur disabled by runtime failure", throwable)
                }
                result
            }
    }

    private fun hookCancel(loader: ClassLoader?) {
        val animatorClass = findClass(SURFACE_ANIMATOR, loader) ?: return
        val method = animatorClass.getDeclaredMethod(
            "cancelAnimation",
            SurfaceControl.Transaction::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val animation = runCatching {
                    readField(chain.thisObject, "mAnimation") as? FluidAnimation
                }.getOrNull()
                try {
                    if (animation != null && settings.cancelFix) {
                        collapseImmediately(chain.thisObject, animation)
                    }
                    val result = chain.proceed()
                    if (animation != null && animation.enter && settings.cancelFix) {
                        finishInterruptedActivity(chain.thisObject)
                    }
                    result
                } catch (throwable: Throwable) {
                    log(Log.WARN, TAG, "Cancel guard failed", throwable)
                    chain.proceed()
                }
            }
    }

    private fun collapseImmediately(animator: Any, animation: FluidAnimation) {
        val transaction = readField(animator, "mAnimatable")?.let { animatable ->
            runCatching {
                animatable.javaClass.getMethod("getSyncTransaction").invoke(animatable)
            }.getOrNull()
        } as? SurfaceControl.Transaction ?: return
        val leash = readField(animator, "mLeash") as? SurfaceControl ?: return
        if (!leash.isValid) return
        transaction.setScale(leash, 0.10f, 0.10f)
        transaction.setAlpha(leash, 0f)
        transaction.apply()
    }

    private fun finishInterruptedActivity(animator: Any) {
        val activityRecord = readField(animator, "mAnimatable") ?: return
        val isActivity = runCatching {
            activityRecord.javaClass.getMethod("asActivityRecord").invoke(activityRecord)
        }.getOrNull() ?: return
        runCatching {
            isActivity.javaClass.getMethod("removeImmediately").invoke(isActivity)
        }.onFailure {
            log(Log.WARN, TAG, "Interrupted activity finish failed", it)
        }
    }

    private fun hookZeroLatency(loader: ClassLoader?) {
        val appTransitionClass = findClass(APP_TRANSITION, loader) ?: return
        val canSkip = appTransitionClass.getDeclaredMethod("canSkipFirstFrame")
        hook(canSkip)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                try {
                    val original = chain.proceed() as? Boolean ?: false
                    val current = settings
                    if (!current.enabled || !current.zeroLatency || original) {
                        return@intercept original
                    }
                    val nextType = readField(
                        chain.thisObject,
                        "mNextAppTransitionType"
                    ) as? Int ?: 0
                    return@intercept nextType in 1..9
                } catch (throwable: Throwable) {
                    log(Log.WARN, TAG, "Zero latency hook failed", throwable)
                    return@intercept chain.proceed()
                }
            }
    }

    private fun isAppTransition(transit: Int): Boolean {
        return transit in APP_TRANSITIONS
    }

    private fun isFluidTarget(target: Any?): Boolean {
        if (target == null) return false
        val activity = runCatching {
            target.javaClass.getMethod("asActivityRecord").invoke(target)
        }.getOrNull()
        if (activity != null) return true

        val task = runCatching {
            target.javaClass.getMethod("asTask").invoke(target)
        }.getOrNull() ?: return false
        val homeOrRecents = runCatching {
            task.javaClass.getMethod("isActivityTypeHomeOrRecents").invoke(task) as? Boolean
        }.getOrNull()
        return homeOrRecents != true
    }

    private fun readField(target: Any?, name: String): Any? {
        if (target == null) return null
        val field = fields.getOrPut(target.javaClass.name + ":" + name) {
            runCatching {
                var currentClass: Class<*>? = target.javaClass
                while (currentClass != null) {
                    runCatching {
                        return@runCatching currentClass.getDeclaredField(name).apply {
                            isAccessible = true
                        }
                    }
                    currentClass = currentClass.superclass
                }
                null
            }.getOrNull()
        }
        return runCatching { field?.get(target) }.getOrNull()
    }

    companion object {
        private const val TAG = "AOSPFluidAnimations"
        private const val SYSTEM_PACKAGE = "android"
        private const val PREFERENCES_GROUP = "fluid_animations"
        private const val WINDOW_CONTAINER = "com.android.server.wm.WindowContainer"
        private const val WINDOW_ANIMATION_SPEC = "com.android.server.wm.WindowAnimationSpec"
        private const val SURFACE_ANIMATOR = "com.android.server.wm.SurfaceAnimator"
        private const val APP_TRANSITION = "com.android.server.wm.AppTransition"
        private val APP_TRANSITIONS = setOf(6, 7, 8, 9, 10, 11, 16)
    }
}
