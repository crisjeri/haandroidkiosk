package cz.hykel.hakiosk

import kotlin.math.abs

class MotionDetector(private val noiseFloor: Int = 8) {
    data class Result(val score: Double, val motionDetected: Boolean)

    private var previousFrame: ByteArray? = null

    fun analyze(frame: ByteArray, sensitivity: Int): Result {
        val previous = previousFrame
        previousFrame = frame.copyOf()
        if (previous == null || previous.size != frame.size || frame.isEmpty()) return Result(0.0, false)

        var differenceSum = 0L
        for (index in frame.indices) {
            val difference = abs((frame[index].toInt() and 0xff) - (previous[index].toInt() and 0xff))
            if (difference >= noiseFloor) differenceSum += difference
        }
        val score = differenceSum.toDouble() / frame.size
        val threshold = 2.0 + (100 - sensitivity.coerceIn(1, 100)) * 0.18
        return Result(score, score >= threshold)
    }

    fun reset() {
        previousFrame = null
    }
}
