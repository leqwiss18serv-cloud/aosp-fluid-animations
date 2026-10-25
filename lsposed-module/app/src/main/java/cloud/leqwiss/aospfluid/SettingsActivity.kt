package cloud.leqwiss.aospfluid

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var profileGroup: RadioGroup
    private lateinit var profileHyper: RadioButton
    private lateinit var profileColor: RadioButton
    private lateinit var profileOrigin: RadioButton
    private lateinit var speedSlider: Slider
    private lateinit var speedValue: TextView
    private lateinit var cancelFixSwitch: MaterialSwitch

    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        bindViews()
        installListeners()
        ServiceHolder.addListener { loadSettings() }
        loadSettings()
    }

    private fun bindViews() {
        statusView = findViewById(R.id.statusView)
        profileGroup = findViewById(R.id.profileGroup)
        profileHyper = findViewById(R.id.profileHyper)
        profileColor = findViewById(R.id.profileColor)
        profileOrigin = findViewById(R.id.profileOrigin)
        speedSlider = findViewById(R.id.speedSlider)
        speedValue = findViewById(R.id.speedValue)
        cancelFixSwitch = findViewById(R.id.cancelFixSwitch)
        findViewById<MaterialButton>(R.id.resetButton).setOnClickListener { resetSettings() }
    }

    private fun installListeners() {
        profileGroup.setOnCheckedChangeListener { _, _ -> saveSettings() }
        speedSlider.addOnChangeListener { _, _, _ -> saveSettings() }
        cancelFixSwitch.setOnCheckedChangeListener { _, _ -> saveSettings() }
    }

    private fun loadSettings() {
        val preferences = preferences() ?: return setServiceMissing()
        runCatching {
            updatingUi = true
            val settings = AnimationSettings.read(preferences)
            when (settings.profile) {
                AnimationProfile.HYPEROS -> profileHyper.isChecked = true
                AnimationProfile.COLOROS -> profileColor.isChecked = true
                AnimationProfile.ORIGINOS -> profileOrigin.isChecked = true
            }
            speedSlider.value = settings.speed
            cancelFixSwitch.isChecked = settings.cancelFix
            updateLabels()
            statusView.text = if (ServiceHolder.service == null) {
                getString(R.string.status_local_only)
            } else {
                getString(R.string.status_ready)
            }
            updatingUi = false
        }.onFailure {
            updatingUi = false
            statusView.text = getString(R.string.status_read_failed)
        }
    }

    private fun saveSettings() {
        if (updatingUi) return
        val preferences = preferences() ?: return setServiceMissing()
        runCatching {
            preferences.edit()
                .putBoolean(AnimationSettings.KEY_ENABLED, true)
                .putString(AnimationSettings.KEY_PROFILE, selectedProfile().key)
                .putInt(AnimationSettings.KEY_SPEED, (speedSlider.value * 100f).toInt())
                .putBoolean(AnimationSettings.KEY_CANCEL_FIX, cancelFixSwitch.isChecked)
                .apply()
            updateLabels()
            statusView.text = if (ServiceHolder.service == null) {
                getString(R.string.status_local_only)
            } else {
                getString(R.string.status_saved)
            }
        }.onFailure {
            statusView.text = getString(R.string.status_save_failed)
        }
    }

    private fun resetSettings() {
        val preferences = preferences() ?: return setServiceMissing()
        runCatching {
            preferences.edit()
                .putBoolean(AnimationSettings.KEY_ENABLED, true)
                .putString(AnimationSettings.KEY_PROFILE, AnimationProfile.ORIGINOS.key)
                .putInt(AnimationSettings.KEY_SPEED, 150)
                .putBoolean(AnimationSettings.KEY_CANCEL_FIX, true)
                .apply()
            loadSettings()
            statusView.text = getString(R.string.status_reset)
        }.onFailure {
            statusView.text = getString(R.string.status_save_failed)
        }
    }

    private fun preferences(): SharedPreferences? {
        return ServiceHolder.service?.getRemotePreferences(PREFERENCES_GROUP)
            ?: getSharedPreferences(LOCAL_PREFERENCES, MODE_PRIVATE)
    }

    private fun setServiceMissing() {
        statusView.text = getString(R.string.status_service_missing)
    }

    private fun selectedProfile(): AnimationProfile {
        return when {
            profileHyper.isChecked -> AnimationProfile.HYPEROS
            profileColor.isChecked -> AnimationProfile.COLOROS
            else -> AnimationProfile.ORIGINOS
        }
    }

    private fun updateLabels() {
        speedValue.text = getString(R.string.speed_value, speedSlider.value)
    }

    companion object {
        private const val PREFERENCES_GROUP = "fluid_animations"
        private const val LOCAL_PREFERENCES = "fluid_animations_local"
    }
}
