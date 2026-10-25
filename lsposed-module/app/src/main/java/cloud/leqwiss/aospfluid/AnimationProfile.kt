package cloud.leqwiss.aospfluid

import android.content.SharedPreferences

enum class AnimationProfile(
    val key: String,
    val title: String,
    val description: String,
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
        description = "Резкий старт и тяжёлое торможение",
        openDuration = 470L,
        closeDuration = 330L,
        openScale = 0.78f,
        closeScale = 0.86f,
        openTravel = 0.018f,
        closeTravel = 0.014f
    ),
    COLOROS(
        key = "coloros",
        title = "ColorOS",
        description = "Мягкая пружина Fluid Cloud",
        openDuration = 520L,
        closeDuration = 370L,
        openScale = 0.82f,
        closeScale = 0.90f,
        openTravel = 0.010f,
        closeTravel = 0.008f
    ),
    ORIGINOS(
        key = "originos",
        title = "OriginOS 7",
        description = "Живая пружина с отскоком и глубиной",
        openDuration = 580L,
        closeDuration = 420L,
        openScale = 0.70f,
        closeScale = 0.80f,
        openTravel = 0.028f,
        closeTravel = 0.022f
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
    val cancelFix: Boolean
) {
    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PROFILE = "profile"
        const val KEY_SPEED = "speed"
        const val KEY_CANCEL_FIX = "cancel_fix"

        val DEFAULT = AnimationSettings(
            enabled = true,
            profile = AnimationProfile.ORIGINOS,
            speed = 1.5f,
            cancelFix = true
        )

        val STOCK = AnimationSettings(
            enabled = false,
            profile = AnimationProfile.ORIGINOS,
            speed = 1.0f,
            cancelFix = false
        )

        fun read(preferences: SharedPreferences): AnimationSettings {
            return AnimationSettings(
                enabled = preferences.readBoolean(KEY_ENABLED, true),
                profile = AnimationProfile.fromKey(
                    preferences.getString(KEY_PROFILE, AnimationProfile.ORIGINOS.key)
                ),
                speed = (preferences.readInt(KEY_SPEED, 150, 50, 250) / 100f)
                    .coerceIn(0.5f, 2.5f),
                cancelFix = preferences.readBoolean(KEY_CANCEL_FIX, true)
            )
        }
    }
}
