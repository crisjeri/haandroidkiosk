package cz.hykel.hakiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class ScreenController(private val context: Context) {
    fun apply(action: ScreenStateMachine.Action) {
        when (action) {
            ScreenStateMachine.Action.NONE -> Unit
            ScreenStateMachine.Action.ACTIVATE -> activate()
            ScreenStateMachine.Action.DIM -> broadcast(KioskActions.SCREEN_DIMMED)
            ScreenStateMachine.Action.TURN_OFF -> turnOff()
        }
    }

    @Suppress("DEPRECATION")
    private fun activate() {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "HaKiosk:MotionWake",
        )
        wakeLock.acquire(3_000L)
        broadcast(KioskActions.SCREEN_ACTIVE)
    }

    private fun turnOff() {
        broadcast(KioskActions.SCREEN_OFF)
        val policyManager = context.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(context, DeviceAdminReceiverImpl::class.java)
        if (policyManager.isAdminActive(admin)) {
            runCatching { policyManager.lockNow() }
                .onFailure { AppLog.warn(TAG, "Device Admin nedokázal uzamknout zařízení", it) }
        }
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
    }

    private companion object {
        const val TAG = "HaKiosk.Screen"
    }
}
