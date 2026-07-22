package cz.hykel.hakiosk

data class BatteryControlStatus(val slateMode: Int)

object BatteryControlStatusParser {
    private const val MAX_AGE_SECONDS = 180L

    fun parse(content: String, nowEpochSeconds: Long): BatteryControlStatus? {
        val values = content.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()

        if (values["version"] != "1") return null
        val updatedEpochSeconds = values["updated_epoch_seconds"]?.toLongOrNull() ?: return null
        val ageSeconds = nowEpochSeconds - updatedEpochSeconds
        if (ageSeconds !in 0..MAX_AGE_SECONDS) return null

        val slateMode = values["slate_mode"]?.toIntOrNull()?.takeIf { it == 0 || it == 1 } ?: return null
        return BatteryControlStatus(slateMode)
    }
}
