package cloud.leqwiss.aospfluid

import android.content.SharedPreferences

enum class AnimationProfile(
    val key: String,
    val title: String,
    val openDuration: Long,
    val closeDuration: Long,
    val openScale: Float,
    val closeScale: Float,
    val openTravel: Float,
    val closeTravel: Float
) {
    HYPEROS(
        key = "hyperos",
        title = "HyperOS 4",
        openDuration = 430L,
        closeDuration = 300L,
        openScale = 0.85f,
        closeScale = 0.91f,
        openTravel = 0.012f,
        closeTravel = 0.008f
    ),
    COLOROS(
        key = "coloros",
        title = "ColorOS",
        openDuration = 470L,
        closeDuration = 340L,
        openScale = 0.88f,
        closeScale = 0.94f,
        openTravel = 0.004f,
        closeTravel = 0.004f
    ),
    ORIGINOS(
        key = "originos",
        title = "OriginOS 7",
        openDuration = 540L,
        closeDuration = 380L,
        openScale = 0.82f,
        closeScale = 0.88f,
        openTravel = 0.018f,
        closeTravel = 0.014f
    );

    companion object {
        fun fromKey(value: String?): AnimationProfile {
            return entries.firstOrNull { it.key == value } ?: ORIGINOS
        }
    }
}

data class AnimationSettings(
    val enabled: Boolean,
    val profile: AnimationProfile,
    val speed: Float,
    val colorBlur: Boolean,
    val blurRadius: Int,
    val cancelFix: Boolean,
    val zeroLatency: Boolean
) {
    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PROFILE = "profile"
        const val KEY_SPEED = "speed"
        const val KEY_COLOR_BLUR = "coloros_blur"
        const val KEY_BLUR_RADIUS = "blur_radius"
        const val KEY_CANCEL_FIX = "cancel_fix"
        const val KEY_ZERO_LATENCY = "zero_latency"

        val DEFAULT = AnimationSettings(
            enabled = true,
            profile = AnimationProfile.ORIGINOS,
            speed = 1.5f,
            colorBlur = true,
            blurRadius = 18,
            cancelFix = true,
            zeroLatency = true
        )

        fun read(preferences: SharedPreferences): AnimationSettings {
            return AnimationSettings(
                enabled = preferences.readBoolean(KEY_ENABLED, true),
                profile = AnimationProfile.fromKey(
                    preferences.getString(KEY_PROFILE, AnimationProfile.ORIGINOS.key)
                ),
                speed = (preferences.readInt(KEY_SPEED, 150, 50, 250) / 100f)
                    .coerceIn(0.5f, 2.5f),
                colorBlur = preferences.readBoolean(KEY_COLOR_BLUR, true),
                blurRadius = preferences.readInt(KEY_BLUR_RADIUS, 18, 0, 36),
                cancelFix = preferences.readBoolean(KEY_CANCEL_FIX, true),
                zeroLatency = preferences.readBoolean(KEY_ZERO_LATENCY, true)
            )
        }
    }
}
