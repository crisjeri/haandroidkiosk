package cz.hykel.hakiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.ComponentActivity

class DiagnosticsActivity : ComponentActivity() {
    private lateinit var batteryDiagnosticsReader: BatteryDiagnosticsReader
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationController.apply(this, PreferencesRepository(this).screenOrientation)
        setContentView(R.layout.activity_diagnostics)
        batteryDiagnosticsReader = BatteryDiagnosticsReader(this)
    }

    override fun onResume() {
        super.onResume()
        OrientationController.apply(this, PreferencesRepository(this).screenOrientation)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun render() {
        val preferences = PreferencesRepository(this)
        val webViewPackage = WebView.getCurrentWebViewPackage()
        val adminActive = getSystemService(DevicePolicyManager::class.java)
            .isAdminActive(ComponentName(this, DeviceAdminReceiverImpl::class.java))
        val lastMotion = RuntimeStatus.lastMotionElapsedRealtime
        val motionAge = if (lastMotion == 0L) "nikdy" else "před ${(SystemClock.elapsedRealtime() - lastMotion) / 1_000} s"
        val battery = batteryDiagnosticsReader.read()
        val text = buildString {
            appendLine("Verze aplikace: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}")
            appendLine("Android System WebView: ${webViewPackage?.packageName ?: "neznámý"} ${webViewPackage?.versionName ?: ""}")
            appendLine("URL: ${preferences.homeUrl}")
            appendLine("Síť: ${if (RuntimeStatus.networkAvailable) "dostupná" else "offline"}")
            appendLine("Kamera: ${RuntimeStatus.cameraState}")
            appendLine("Foreground služba: ${if (RuntimeStatus.serviceRunning) "běží" else "neběží"}")
            appendLine("Baterie: ${battery.capacityPercent?.let { "$it %" } ?: "nedostupná"}")
            appendLine("Stav baterie: ${battery.status}")
            if (battery.chargeControl == null) {
                appendLine("Řízení nabíjení: nedostupné")
            } else {
                appendLine("Řízení nabíjení: dostupné")
                val slateDescription = if (battery.chargeControl.slateMode == 0) "nabíjení povoleno" else "nabíjení zakázáno"
                appendLine("batt_slate_mode: ${battery.chargeControl.slateMode} ($slateDescription)")
            }
            appendLine("Poslední detekce: $motionAge")
            appendLine("Hodnota pohybu: ${"%.2f".format(RuntimeStatus.motionValue)}")
            appendLine("Device Admin: ${if (adminActive) "aktivní" else "neaktivní"}")
            appendLine("Poslední chyba WebView: ${RuntimeStatus.lastWebViewError ?: "žádná"}")
        }
        findViewById<TextView>(R.id.diagnostic_text).text = text
    }
}
