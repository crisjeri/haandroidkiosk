package cz.hykel.hakiosk

import android.Manifest
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class SettingsActivity : ComponentActivity() {
    private lateinit var preferences: PreferencesRepository
    private lateinit var pinManager: PinManager
    private val firstRun: Boolean get() = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferencesRepository(this)
        OrientationController.apply(this, preferences.screenOrientation)
        setContentView(R.layout.activity_settings)
        pinManager = PinManager(preferences.raw)
        populateFields()
        bindActions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (firstRun && !preferences.isConfigured) {
                    Toast.makeText(this@SettingsActivity, R.string.home_assistant_url, Toast.LENGTH_SHORT).show()
                } else {
                    finish()
                }
            }
        })
        if (!pinManager.hasPin()) showCreatePinDialog(cancelable = false)
    }

    override fun onResume() {
        super.onResume()
        updateDeviceAdminStatus()
    }

    private fun populateFields() {
        edit(R.id.home_url).setText(preferences.homeUrl)
        check(R.id.allow_http).isChecked = preferences.allowHttp
        check(R.id.reload_on_wake).isChecked = preferences.reloadOnWake
        check(R.id.reload_on_network).isChecked = preferences.reloadOnNetwork
        check(R.id.motion_enabled).isChecked = preferences.motionEnabled
        check(R.id.diagnostic_mode).isChecked = preferences.diagnosticMode
        edit(R.id.sensitivity).setText(preferences.sensitivity.toString())
        edit(R.id.analysis_fps).setText(preferences.analysisFps.toString())
        edit(R.id.dim_timeout).setText(preferences.dimTimeoutSeconds.toString())
        edit(R.id.off_delay).setText(preferences.offDelaySeconds.toString())
        edit(R.id.active_brightness).setText(preferences.activeBrightnessPercent.toString())
        edit(R.id.dim_brightness).setText(preferences.dimBrightnessPercent.toString())
        findViewById<Spinner>(R.id.screen_orientation).setSelection(
            preferences.screenOrientation.coerceIn(
                OrientationController.LANDSCAPE,
                OrientationController.REVERSE_PORTRAIT,
            ),
        )
        check(R.id.lock_task).isChecked = preferences.lockTaskEnabled
        check(R.id.start_after_boot).isChecked = preferences.startAfterBoot
        check(R.id.open_after_boot).isChecked = preferences.openAfterBoot
    }

    private fun bindActions() {
        findViewById<View>(R.id.save).setOnClickListener { save() }
        findViewById<View>(R.id.request_camera).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
        }
        findViewById<View>(R.id.activate_admin).setOnClickListener { activateDeviceAdmin() }
        findViewById<View>(R.id.deactivate_admin).setOnClickListener { confirmDeactivateDeviceAdmin() }
        findViewById<View>(R.id.change_pin).setOnClickListener { showCreatePinDialog(cancelable = true) }
        findViewById<View>(R.id.test_camera).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            } else {
                startMotionService()
                Toast.makeText(this, R.string.service_started, Toast.LENGTH_LONG).show()
            }
        }
        findViewById<View>(R.id.reload_page).setOnClickListener {
            sendBroadcast(Intent(KioskActions.RELOAD_WEBVIEW).setPackage(packageName))
        }
        findViewById<View>(R.id.clear_cache).setOnClickListener { clearWebViewCache() }
        findViewById<View>(R.id.diagnostics).setOnClickListener {
            startActivity(Intent().setClassName(this, "$packageName.DiagnosticsActivity"))
        }
        findViewById<View>(R.id.exit_kiosk).setOnClickListener {
            sendBroadcast(Intent(KioskActions.EXIT_KIOSK).setPackage(packageName))
            finishAffinity()
        }
    }

    private fun save() {
        val allowHttp = check(R.id.allow_http).isChecked
        val url = UrlValidator.validate(edit(R.id.home_url).text.toString(), allowHttp)
        if (!url.isValid) {
            edit(R.id.home_url).error = url.error
            return
        }
        val numbers = try {
            listOf(
                edit(R.id.sensitivity).text.toString().toInt().coerceIn(1, 100),
                edit(R.id.analysis_fps).text.toString().toInt().coerceIn(1, 3),
                edit(R.id.dim_timeout).text.toString().toInt().coerceAtLeast(5),
                edit(R.id.off_delay).text.toString().toInt().coerceAtLeast(0),
                edit(R.id.active_brightness).text.toString().toInt().coerceIn(1, 100),
                edit(R.id.dim_brightness).text.toString().toInt().coerceIn(1, 100),
            )
        } catch (_: NumberFormatException) {
            Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show()
            return
        }
        preferences.homeUrl = requireNotNull(url.normalizedUrl)
        preferences.allowHttp = allowHttp
        preferences.reloadOnWake = check(R.id.reload_on_wake).isChecked
        preferences.reloadOnNetwork = check(R.id.reload_on_network).isChecked
        preferences.motionEnabled = check(R.id.motion_enabled).isChecked
        preferences.diagnosticMode = check(R.id.diagnostic_mode).isChecked
        preferences.sensitivity = numbers[0]
        preferences.analysisFps = numbers[1]
        preferences.dimTimeoutSeconds = numbers[2]
        preferences.offDelaySeconds = numbers[3]
        preferences.activeBrightnessPercent = numbers[4]
        preferences.dimBrightnessPercent = numbers[5]
        preferences.screenOrientation = findViewById<Spinner>(R.id.screen_orientation).selectedItemPosition
        preferences.lockTaskEnabled = check(R.id.lock_task).isChecked
        preferences.startAfterBoot = check(R.id.start_after_boot).isChecked
        preferences.openAfterBoot = check(R.id.open_after_boot).isChecked
        if (preferences.motionEnabled && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            stopService(Intent().setClassName(this, "$packageName.MotionDetectionService"))
            startMotionService()
        } else {
            stopService(Intent().setClassName(this, "$packageName.MotionDetectionService"))
        }
        sendBroadcast(Intent(KioskActions.CONFIG_CHANGED).setPackage(packageName))
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun showCreatePinDialog(cancelable: Boolean) {
        val first = pinEdit(R.string.pin_hint)
        val second = pinEdit(R.string.pin_repeat_hint)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(first)
            addView(second)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (pinManager.hasPin()) R.string.pin_title_change else R.string.pin_title_create)
            .setView(container)
            .setPositiveButton(R.string.confirm, null)
            .apply { if (cancelable) setNegativeButton(R.string.cancel, null) }
            .setCancelable(cancelable)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = first.text.toString()
                if (pin.length < 4 || pin.any { !it.isDigit() } || pin != second.text.toString()) {
                    second.error = getString(R.string.pin_mismatch)
                } else {
                    pinManager.setPin(pin)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun activateDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation))
        }
        startActivity(intent)
    }

    private fun updateDeviceAdminStatus() {
        val active = getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent())
        val timeoutSeconds = Settings.System.getLong(
            contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            60_000L,
        ) / 1_000L
        findViewById<android.widget.TextView>(R.id.admin_status).text = if (active) {
            getString(R.string.admin_status_active)
        } else {
            getString(R.string.admin_status_inactive, timeoutSeconds)
        }
        findViewById<View>(R.id.activate_admin).visibility = if (active) View.GONE else View.VISIBLE
        findViewById<View>(R.id.deactivate_admin).visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun confirmDeactivateDeviceAdmin() {
        AlertDialog.Builder(this)
            .setMessage(R.string.admin_remove_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(adminComponent())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearWebViewCache() {
        WebView(this).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun startMotionService() {
        startForegroundService(Intent().setClassName(this, "$packageName.MotionDetectionService"))
    }

    private fun adminComponent() = ComponentName(this, DeviceAdminReceiverImpl::class.java)
    private fun edit(id: Int): EditText = findViewById(id)
    private fun check(id: Int): CheckBox = findViewById(id)
    private fun pinEdit(hint: Int) = EditText(this).apply {
        setHint(hint)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            preferences.motionEnabled = false
            check(R.id.motion_enabled).isChecked = false
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_FIRST_RUN = "first_run"
        private const val CAMERA_REQUEST = 101
    }
}
