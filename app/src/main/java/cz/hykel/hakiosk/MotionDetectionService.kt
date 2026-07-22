package cz.hykel.hakiosk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

class MotionDetectionService : Service(), CameraController.Listener {
    private lateinit var preferences: PreferencesRepository
    private lateinit var cameraController: CameraController
    private lateinit var screenController: ScreenController
    private lateinit var stateMachine: ScreenStateMachine
    private val detector = MotionDetector()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraRetry = RetryPolicy(5_000L, 5 * 60_000L)
    private var cameraRetryAttempt = 0
    private var stopping = false

    private val tick = object : Runnable {
        override fun run() {
            if (!stopping) {
                screenController.apply(stateMachine.tick(SystemClock.elapsedRealtime()))
                mainHandler.postDelayed(this, 1_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
        screenController = ScreenController(this)
        val now = SystemClock.elapsedRealtime()
        stateMachine = ScreenStateMachine(
            preferences.dimTimeoutSeconds * 1_000L,
            preferences.offDelaySeconds * 1_000L,
            now,
        )
        cameraController = CameraController(this, { preferences.analysisFps }, this)
        createNotificationChannel()
        RuntimeStatus.serviceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_starting)))
        if (intent?.action == KioskActions.DISABLE_MOTION) {
            preferences.motionEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!preferences.motionEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            RuntimeStatus.cameraState = getString(R.string.camera_permission_missing)
            updateNotification(RuntimeStatus.cameraState)
            stopSelf()
            return START_NOT_STICKY
        }
        cameraController.start()
        mainHandler.removeCallbacks(tick)
        mainHandler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        stopping = true
        mainHandler.removeCallbacksAndMessages(null)
        cameraController.stop()
        detector.reset()
        RuntimeStatus.serviceRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onFrame(luma: ByteArray, width: Int, height: Int, timestampMillis: Long) {
        val result = detector.analyze(luma, preferences.sensitivity)
        RuntimeStatus.motionValue = result.score
        if (result.motionDetected) {
            RuntimeStatus.lastMotionElapsedRealtime = timestampMillis
            mainHandler.post { screenController.apply(stateMachine.onMotion(timestampMillis)) }
        }
    }

    override fun onCameraState(state: String) {
        RuntimeStatus.cameraState = state
        if (state.startsWith("Aktivní")) cameraRetryAttempt = 0
        mainHandler.post { updateNotification(state) }
    }

    override fun onCameraError(message: String, error: Throwable?) {
        RuntimeStatus.cameraState = message
        AppLog.warn(TAG, message, error)
        mainHandler.post {
            updateNotification(message)
            if (!stopping && preferences.motionEnabled &&
                checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ) {
                val delay = cameraRetry.delayMillis(cameraRetryAttempt++)
                mainHandler.postDelayed({ cameraController.start() }, delay)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disableIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MotionDetectionService::class.java).setAction(KioskActions.DISABLE_MOTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(state)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, getString(R.string.notification_disable), disableIntent).build())
            .build()
    }

    private fun updateNotification(state: String) {
        if (!stopping) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private companion object {
        const val TAG = "HaKiosk.MotionService"
        const val CHANNEL_ID = "motion_detection"
        const val NOTIFICATION_ID = 1001
    }
}
