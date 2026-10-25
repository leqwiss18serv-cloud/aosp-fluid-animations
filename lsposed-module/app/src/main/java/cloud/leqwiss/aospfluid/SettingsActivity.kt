package cloud.leqwiss.aospfluid

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var profileGroup: RadioGroup
    private lateinit var profileHyper: RadioButton
    private lateinit var profileColor: RadioButton
    private lateinit var profileOrigin: RadioButton
    private lateinit var profileDescription: TextView
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var speedSlider: Slider
    private lateinit var speedValue: TextView
    private lateinit var cancelFixSwitch: MaterialSwitch
    private lateinit var applyButton: MaterialButton
    private lateinit var resetButton: MaterialButton

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
        profileDescription = findViewById(R.id.profileDescription)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        speedSlider = findViewById(R.id.speedSlider)
        speedValue = findViewById(R.id.speedValue)
        cancelFixSwitch = findViewById(R.id.cancelFixSwitch)
        applyButton = findViewById(R.id.applyButton)
        resetButton = findViewById(R.id.resetButton)
        applyButton.setOnClickListener { applySettings() }
        resetButton.setOnClickListener { resetStock() }
    }

    private fun installListeners() {
        profileGroup.setOnCheckedChangeListener { _, _ -> updateDescription() }
        speedSlider.addOnChangeListener { _, value, _ ->
            speedValue.text = getString(R.string.speed_value, value)
        }
    }

    private fun loadSettings() {
        val preferences = preferences() ?: return
        runCatching {
            updatingUi = true
            val settings = AnimationSettings.read(preferences)
            enabledSwitch.isChecked = settings.enabled
            when (settings.profile) {
                AnimationProfile.HYPEROS -> profileHyper.isChecked = true
                AnimationProfile.COLOROS -> profileColor.isChecked = true
                AnimationProfile.ORIGINOS -> profileOrigin.isChecked = true
            }
            speedSlider.value = settings.speed
            cancelFixSwitch.isChecked = settings.cancelFix
            updateDescription()
            updateStatus()
            updatingUi = false
        }.onFailure {
            updatingUi = false
            showError(getString(R.string.status_read_failed))
        }
    }

    private fun applySettings() {
        val settings = AnimationSettings(
            enabled = enabledSwitch.isChecked,
            profile = selectedProfile(),
            speed = speedSlider.value,
            cancelFix = cancelFixSwitch.isChecked
        )
        if (writeSettings(settings)) {
            showSuccess(getString(R.string.status_applied))
        } else {
            showError(getString(R.string.status_save_failed))
        }
    }

    private fun resetStock() {
        if (writeSettings(AnimationSettings.STOCK)) {
            loadSettings()
            showSuccess(getString(R.string.status_stock))
        } else {
            showError(getString(R.string.status_save_failed))
        }
    }

    private fun writeSettings(settings: AnimationSettings): Boolean {
        return runCatching {
            var wroteRemote = false
            ServiceHolder.service?.getRemotePreferences(PREFERENCES_GROUP)?.edit()?.apply {
                putBoolean(AnimationSettings.KEY_ENABLED, settings.enabled)
                putString(AnimationSettings.KEY_PROFILE, settings.profile.key)
                putInt(AnimationSettings.KEY_SPEED, (settings.speed * 100f).toInt())
                putBoolean(AnimationSettings.KEY_CANCEL_FIX, settings.cancelFix)
            }?.apply()
            wroteRemote = ServiceHolder.service != null

            localPreferences().edit().apply {
                putBoolean(AnimationSettings.KEY_ENABLED, settings.enabled)
                putString(AnimationSettings.KEY_PROFILE, settings.profile.key)
                putInt(AnimationSettings.KEY_SPEED, (settings.speed * 100f).toInt())
                putBoolean(AnimationSettings.KEY_CANCEL_FIX, settings.cancelFix)
            }.apply()
            wroteRemote
        }.getOrDefault(false)
    }

    private fun preferences(): SharedPreferences? {
        return ServiceHolder.service?.getRemotePreferences(PREFERENCES_GROUP)
            ?: localPreferences()
    }

    private fun localPreferences(): SharedPreferences {
        return getSharedPreferences(LOCAL_PREFERENCES, MODE_PRIVATE)
    }

    private fun selectedProfile(): AnimationProfile {
        return when {
            profileHyper.isChecked -> AnimationProfile.HYPEROS
            profileColor.isChecked -> AnimationProfile.COLOROS
            else -> AnimationProfile.ORIGINOS
        }
    }

    private fun updateDescription() {
        profileDescription.text = selectedProfile().description
    }

    private fun updateStatus() {
        if (ServiceHolder.service == null) {
            showWarning(getString(R.string.status_local_only))
        } else {
            showSuccess(getString(R.string.status_ready))
        }
    }

    private fun showSuccess(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.status_success))
    }

    private fun showWarning(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
    }

    private fun showError(message: String) {
        statusView.text = message
        statusView.setTextColor(ContextCompat.getColor(this, R.color.status_error))
    }

    companion object {
        private const val PREFERENCES_GROUP = "fluid_animations"
        private const val LOCAL_PREFERENCES = "fluid_animations_local"
    }
}
