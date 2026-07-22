package cz.hykel.hakiosk

class RetryPolicy(
    private val baseDelayMillis: Long = 2_000L,
    private val maximumDelayMillis: Long = 60_000L,
) {
    fun delayMillis(attempt: Int): Long {
        val exponent = attempt.coerceIn(0, 30)
        val multiplier = 1L shl exponent
        return if (baseDelayMillis > maximumDelayMillis / multiplier) {
            maximumDelayMillis
        } else {
            (baseDelayMillis * multiplier).coerceAtMost(maximumDelayMillis)
        }
    }
}
