package cz.hykel.hakiosk

import android.content.Context
import android.content.SharedPreferences

class PreferencesRepository(context: Context) {
    val raw: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var homeUrl: String
        get() = raw.getString(KEY_HOME_URL, "") ?: ""
        set(value) = raw.edit().putString(KEY_HOME_URL, value).apply()
    var allowHttp: Boolean by booleanPreference(KEY_ALLOW_HTTP, false)
    var startAfterBoot: Boolean by booleanPreference(KEY_START_AFTER_BOOT, true)
    var openAfterBoot: Boolean by booleanPreference(KEY_OPEN_AFTER_BOOT, false)
    var motionEnabled: Boolean by booleanPreference(KEY_MOTION_ENABLED, true)
    var diagnosticMode: Boolean by booleanPreference(KEY_DIAGNOSTIC_MODE, false)
    var reloadOnWake: Boolean by booleanPreference(KEY_RELOAD_ON_WAKE, false)
    var reloadOnNetwork: Boolean by booleanPreference(KEY_RELOAD_ON_NETWORK, true)
    var lockTaskEnabled: Boolean by booleanPreference(KEY_LOCK_TASK, false)
    var sensitivity: Int by intPreference(KEY_SENSITIVITY, 18)
    var analysisFps: Int by intPreference(KEY_ANALYSIS_FPS, 2)
    var dimTimeoutSeconds: Int by intPreference(KEY_DIM_TIMEOUT, 60)
    var offDelaySeconds: Int by intPreference(KEY_OFF_DELAY, 30)
    var activeBrightnessPercent: Int by intPreference(KEY_ACTIVE_BRIGHTNESS, 80)
    var dimBrightnessPercent: Int by intPreference(KEY_DIM_BRIGHTNESS, 5)
    var screenOrientation: Int by intPreference(KEY_SCREEN_ORIENTATION, OrientationController.LANDSCAPE)

    val isConfigured: Boolean get() = homeUrl.isNotBlank() && PinManager(raw).hasPin()

    private fun booleanPreference(key: String, default: Boolean) =
        PreferenceDelegate({ raw.getBoolean(key, default) }, { raw.edit().putBoolean(key, it).apply() })

    private fun intPreference(key: String, default: Int) =
        PreferenceDelegate({ raw.getInt(key, default) }, { raw.edit().putInt(key, it).apply() })

    private class PreferenceDelegate<T>(private val getter: () -> T, private val setter: (T) -> Unit) {
        operator fun getValue(thisRef: Any?, property: Any?): T = getter()
        operator fun setValue(thisRef: Any?, property: Any?, value: T) = setter(value)
    }

    companion object {
        const val FILE_NAME = "kiosk_preferences"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_ALLOW_HTTP = "allow_http"
        private const val KEY_START_AFTER_BOOT = "start_after_boot"
        private const val KEY_OPEN_AFTER_BOOT = "open_after_boot"
        private const val KEY_MOTION_ENABLED = "motion_enabled"
        private const val KEY_DIAGNOSTIC_MODE = "diagnostic_mode"
        private const val KEY_RELOAD_ON_WAKE = "reload_on_wake"
        private const val KEY_RELOAD_ON_NETWORK = "reload_on_network"
        private const val KEY_LOCK_TASK = "lock_task"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_ANALYSIS_FPS = "analysis_fps"
        private const val KEY_DIM_TIMEOUT = "dim_timeout"
        private const val KEY_OFF_DELAY = "off_delay"
        private const val KEY_ACTIVE_BRIGHTNESS = "active_brightness"
        private const val KEY_DIM_BRIGHTNESS = "dim_brightness"
        private const val KEY_SCREEN_ORIENTATION = "screen_orientation"
    }
}
