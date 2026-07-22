package cz.hykel.hakiosk

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class MainActivity : ComponentActivity(), NetworkMonitor.Listener {
    private lateinit var preferences: PreferencesRepository
    private lateinit var webView: WebView
    private lateinit var webViewController: WebViewController
    private lateinit var networkMonitor: NetworkMonitor
    private var receiverRegistered = false
    private val gestureHandler = Handler(Looper.getMainLooper())
    private val gestureHoldMillis = 2_000L
    private val gestureMoveTolerance by lazy { 32f * resources.displayMetrics.density }
    private var gesturePointerOne = MotionEvent.INVALID_POINTER_ID
    private var gesturePointerTwo = MotionEvent.INVALID_POINTER_ID
    private var gestureStartOneX = 0f
    private var gestureStartOneY = 0f
    private var gestureStartTwoX = 0f
    private var gestureStartTwoY = 0f
    private var gestureDownTime = 0L
    private var gestureTracking = false
    private var pinDialogShowing = false

    private val openSettingsGesture = Runnable {
        if (!gestureTracking || pinDialogShowing) return@Runnable
        gestureTracking = false
        cancelWebViewTouchGesture()
        showPinDialog()
    }

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                KioskActions.RELOAD_WEBVIEW -> if (::webViewController.isInitialized) webViewController.reload()
                KioskActions.EXIT_KIOSK -> exitKiosk()
                KioskActions.SCREEN_ACTIVE -> applyActiveScreen()
                KioskActions.SCREEN_DIMMED -> applyDimmedScreen()
                KioskActions.SCREEN_OFF -> applyScreenOff()
                KioskActions.CONFIG_CHANGED -> if (::webViewController.isInitialized) webViewController.loadHome()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferencesRepository(this)
        OrientationController.apply(this, preferences.screenOrientation)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.web_view)
        registerActions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webViewController.isInitialized && webView.canGoBack()) webView.goBack()
            }
        })
        if (!preferences.isConfigured) openSettings(firstRun = true) else initializeKiosk()
    }

    override fun onResume() {
        super.onResume()
        OrientationController.apply(this, preferences.screenOrientation)
        enterFullscreen()
        if (preferences.isConfigured && !::webViewController.isInitialized) initializeKiosk()
        CookieManager.getInstance().flush()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        cancelSettingsGesture()
        if (::networkMonitor.isInitialized) networkMonitor.stop()
        if (::webViewController.isInitialized) webViewController.destroy()
        if (receiverRegistered) unregisterReceiver(actionReceiver)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        observeSettingsGesture(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onNetworkStateChanged(available: Boolean) {
        RuntimeStatus.networkAvailable = available
        findViewById<TextView>(R.id.offline_indicator).visibility = if (available) View.GONE else View.VISIBLE
        if (::webViewController.isInitialized) webViewController.onNetworkChanged(available)
    }

    private fun initializeKiosk() {
        if (::webViewController.isInitialized) return
        webViewController = WebViewController(webView, preferences) { RuntimeStatus.lastWebViewError = it }
        webViewController.configure()
        networkMonitor = NetworkMonitor(this, this)
        networkMonitor.start()
        applyActiveScreen()
        webViewController.loadHome()
        if (preferences.lockTaskEnabled) {
            runCatching { startLockTask() }.onFailure { AppLog.warn(TAG, "Lock task mode není dostupný", it) }
        }
        if (preferences.motionEnabled && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startForegroundService(Intent(this, MotionDetectionService::class.java))
        }
    }

    private fun showPinDialog() {
        if (pinDialogShowing) return
        pinDialogShowing = true
        val pin = EditText(this).apply {
            setHint(R.string.pin_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val manager = PinManager(preferences.raw)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.pin_title_enter)
            .setView(pin)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when (manager.verify(pin.text.toString())) {
                    PinManager.Verification.SUCCESS -> {
                        dialog.dismiss()
                        openSettings(firstRun = false)
                    }
                    PinManager.Verification.FAILURE -> pin.error = getString(R.string.pin_invalid)
                    PinManager.Verification.LOCKED -> pin.error = getString(R.string.pin_locked, manager.lockoutSeconds())
                }
            }
        }
        dialog.setOnDismissListener { pinDialogShowing = false }
        dialog.show()
    }

    private fun observeSettingsGesture(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2 && !pinDialogShowing) {
                    gesturePointerOne = event.getPointerId(0)
                    gesturePointerTwo = event.getPointerId(1)
                    gestureStartOneX = event.getX(0)
                    gestureStartOneY = event.getY(0)
                    gestureStartTwoX = event.getX(1)
                    gestureStartTwoY = event.getY(1)
                    gestureDownTime = event.downTime
                    gestureTracking = true
                    gestureHandler.removeCallbacks(openSettingsGesture)
                    gestureHandler.postDelayed(openSettingsGesture, gestureHoldMillis)
                } else {
                    cancelSettingsGesture()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureTracking && (!hasTrackedPointers(event) || movedTooFar(event))) {
                    cancelSettingsGesture()
                }
            }
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> cancelSettingsGesture()
        }
    }

    private fun hasTrackedPointers(event: MotionEvent): Boolean =
        event.findPointerIndex(gesturePointerOne) >= 0 && event.findPointerIndex(gesturePointerTwo) >= 0

    private fun movedTooFar(event: MotionEvent): Boolean {
        val firstIndex = event.findPointerIndex(gesturePointerOne)
        val secondIndex = event.findPointerIndex(gesturePointerTwo)
        if (firstIndex < 0 || secondIndex < 0) return true
        return kotlin.math.abs(event.getX(firstIndex) - gestureStartOneX) > gestureMoveTolerance ||
            kotlin.math.abs(event.getY(firstIndex) - gestureStartOneY) > gestureMoveTolerance ||
            kotlin.math.abs(event.getX(secondIndex) - gestureStartTwoX) > gestureMoveTolerance ||
            kotlin.math.abs(event.getY(secondIndex) - gestureStartTwoY) > gestureMoveTolerance
    }

    private fun cancelSettingsGesture() {
        gestureTracking = false
        gesturePointerOne = MotionEvent.INVALID_POINTER_ID
        gesturePointerTwo = MotionEvent.INVALID_POINTER_ID
        gestureHandler.removeCallbacks(openSettingsGesture)
    }

    private fun cancelWebViewTouchGesture() {
        val now = SystemClock.uptimeMillis()
        val cancelEvent = MotionEvent.obtain(gestureDownTime, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        try {
            super.dispatchTouchEvent(cancelEvent)
        } finally {
            cancelEvent.recycle()
        }
    }

    private fun registerActions() {
        val filter = IntentFilter().apply {
            addAction(KioskActions.RELOAD_WEBVIEW)
            addAction(KioskActions.EXIT_KIOSK)
            addAction(KioskActions.SCREEN_ACTIVE)
            addAction(KioskActions.SCREEN_DIMMED)
            addAction(KioskActions.SCREEN_OFF)
            addAction(KioskActions.CONFIG_CHANGED)
        }
        ContextCompat.registerReceiver(this, actionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun openSettings(firstRun: Boolean) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FIRST_RUN, firstRun))
    }

    private fun applyActiveScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(preferences.activeBrightnessPercent)
        enterFullscreen()
        if (preferences.reloadOnWake && ::webViewController.isInitialized) webViewController.reload()
    }

    private fun applyDimmedScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(preferences.dimBrightnessPercent)
    }

    private fun applyScreenOff() {
        setBrightness(preferences.dimBrightnessPercent)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setBrightness(percent: Int) {
        window.attributes = window.attributes.apply {
            screenBrightness = percent.coerceIn(1, 100) / 100f
        }
    }

    @Suppress("DEPRECATION")
    private fun enterFullscreen() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun exitKiosk() {
        runCatching { stopLockTask() }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        finishAffinity()
    }

    private companion object {
        const val TAG = "HaKiosk.Main"
    }
}
