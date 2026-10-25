package cloud.leqwiss.aospfluid

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var profileGroup: RadioGroup
    private lateinit var profileHyper: com.google.android.material.radioButton.MaterialRadioButton
    private lateinit var profileColor: com.google.android.material.radioButton.MaterialRadioButton
    private lateinit var profileOrigin: com.google.android.material.radioButton.MaterialRadioButton
    private lateinit var speedSlider: Slider
    private lateinit var speedValue: TextView
    private lateinit var blurSwitch: MaterialSwitch
    private lateinit var blurSlider: Slider
    private lateinit var blurValue: TextView
    private lateinit var cancelFixSwitch: MaterialSwitch
    private lateinit var zeroLatencySwitch: MaterialSwitch

    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        bindViews()
        ServiceHolder.addListener { loadSettings() }
        installListeners()
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
        blurSwitch = findViewById(R.id.blurSwitch)
        blurSlider = findViewById(R.id.blurSlider)
        blurValue = findViewById(R.id.blurValue)
        cancelFixSwitch = findViewById(R.id.cancelFixSwitch)
        zeroLatencySwitch = findViewById(R.id.zeroLatencySwitch)
        findViewById<MaterialButton>(R.id.resetButton).setOnClickListener { resetSettings() }
    }

    private fun installListeners() {
        profileGroup.setOnCheckedChangeListener { _, _ -> saveSettings() }
        speedSlider.addOnChangeListener { _, _, _ -> saveSettings() }
        blurSwitch.setOnCheckedChangeListener { _, _ -> saveSettings() }
        blurSlider.addOnChangeListener { _, _, _ -> saveSettings() }
        cancelFixSwitch.setOnCheckedChangeListener { _, _ -> saveSettings() }
        zeroLatencySwitch.setOnCheckedChangeListener { _, _ -> saveSettings() }
    }

    private fun loadSettings() {
        val service = ServiceHolder.service
        if (service == null) {
            statusView.text = getString(R.string.status_service_missing)
            return
        }
        runCatching {
            val prefs = service.getRemotePreferences(PREFERENCES_GROUP)
            updatingUi = true
            when (AnimationSettings.read(prefs).profile) {
                AnimationProfile.HYPEROS -> profileHyper.isChecked = true
                AnimationProfile.COLOROS -> profileColor.isChecked = true
                AnimationProfile.ORIGINOS -> profileOrigin.isChecked = true
            }
            speedSlider.value = prefs.readInt(
                AnimationSettings.KEY_SPEED, 150, 50, 250
            ).toFloat() / 100f
            blurSwitch.isChecked = prefs.readBoolean(AnimationSettings.KEY_COLOR_BLUR, true)
            blurSlider.value = prefs.readInt(
                AnimationSettings.KEY_BLUR_RADIUS, 18, 0, 36
            ).toFloat()
            cancelFixSwitch.isChecked = prefs.readBoolean(AnimationSettings.KEY_CANCEL_FIX, true)
            zeroLatencySwitch.isChecked = prefs.readBoolean(
                AnimationSettings.KEY_ZERO_LATENCY, true
            )
            updateLabels()
            statusView.text = getString(R.string.status_ready)
            updatingUi = false
        }.onFailure {
            updatingUi = false
            statusView.text = getString(R.string.status_read_failed)
        }
    }

    private fun saveSettings() {
        if (updatingUi) return
        val service = ServiceHolder.service ?: return
        runCatching {
            val prefs = service.getRemotePreferences(PREFERENCES_GROUP)
            prefs.edit()
                .putBoolean(AnimationSettings.KEY_ENABLED, true)
                .putString(AnimationSettings.KEY_PROFILE, selectedProfile().key)
                .putInt(AnimationSettings.KEY_SPEED, (speedSlider.value * 100f).toInt())
                .putBoolean(AnimationSettings.KEY_COLOR_BLUR, blurSwitch.isChecked)
                .putInt(AnimationSettings.KEY_BLUR_RADIUS, blurSlider.value.toInt())
                .putBoolean(AnimationSettings.KEY_CANCEL_FIX, cancelFixSwitch.isChecked)
                .putBoolean(AnimationSettings.KEY_ZERO_LATENCY, zeroLatencySwitch.isChecked)
                .apply()
            updateLabels()
            statusView.text = getString(R.string.status_saved)
        }
    }

    private fun resetSettings() {
        val service = ServiceHolder.service ?: return
        runCatching {
            service.getRemotePreferences(PREFERENCES_GROUP).edit()
                .putBoolean(AnimationSettings.KEY_ENABLED, true)
                .putString(AnimationSettings.KEY_PROFILE, AnimationProfile.ORIGINOS.key)
                .putInt(AnimationSettings.KEY_SPEED, 150)
                .putBoolean(AnimationSettings.KEY_COLOR_BLUR, true)
                .putInt(AnimationSettings.KEY_BLUR_RADIUS, 18)
                .putBoolean(AnimationSettings.KEY_CANCEL_FIX, true)
                .putBoolean(AnimationSettings.KEY_ZERO_LATENCY, true)
                .apply()
            loadSettings()
        }
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
        blurValue.text = getString(R.string.blur_radius, blurSlider.value.toInt())
    }

    companion object {
        private const val PREFERENCES_GROUP = "fluid_animations"
    }
}
