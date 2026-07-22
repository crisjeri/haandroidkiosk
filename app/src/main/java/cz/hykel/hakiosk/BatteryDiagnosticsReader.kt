package cz.hykel.hakiosk

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

data class BatteryDiagnostics(
    val capacityPercent: Int?,
    val status: String,
    val chargeControl: BatteryControlStatus?,
)

class BatteryDiagnosticsReader(private val context: Context) {
    fun read(): BatteryDiagnostics {
        val capacity = context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        return BatteryDiagnostics(
            capacityPercent = capacity,
            status = statusText(status),
            chargeControl = readChargeControlStatus(),
        )
    }

    private fun readChargeControlStatus(): BatteryControlStatus? {
        val externalFilesDirectory = context.getExternalFilesDir(null) ?: return null
        val statusFile = File(externalFilesDirectory, STATUS_FILE_NAME)
        if (!statusFile.isFile || statusFile.length() !in 1..MAX_STATUS_FILE_BYTES) return null

        return runCatching {
            BatteryControlStatusParser.parse(
                content = statusFile.readText(),
                nowEpochSeconds = System.currentTimeMillis() / 1_000L,
            )
        }.getOrNull()
    }

    private fun statusText(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "nabíjení"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "vybíjení"
        BatteryManager.BATTERY_STATUS_FULL -> "plně nabitá"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "nenabíjí se"
        else -> "neznámý"
    }

    companion object {
        const val STATUS_FILE_NAME = "battery-charge-control.status"
        private const val MAX_STATUS_FILE_BYTES = 1_024L
    }
}
