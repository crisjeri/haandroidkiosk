package cz.hykel.hakiosk

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = PreferencesRepository(context)
        if (!preferences.isConfigured) return
        if (preferences.startAfterBoot && preferences.motionEnabled &&
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { context.startForegroundService(Intent(context, MotionDetectionService::class.java)) }
                .onFailure { AppLog.error(TAG, "Službu po restartu nelze spustit", it) }
        }
        if (preferences.openAfterBoot) {
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }.onFailure { AppLog.warn(TAG, "Kiosk aktivitu systém po restartu nepovolil", it) }
        }
    }

    private companion object {
        const val TAG = "HaKiosk.Boot"
    }
}
