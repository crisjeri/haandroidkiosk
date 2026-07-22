package cz.hykel.hakiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectorTest {
    @Test fun ignoresNoiseButDetectsMeaningfulChange() {
        val detector = MotionDetector(noiseFloor = 8)
        assertFalse(detector.analyze(ByteArray(100) { 50 }, 100).motionDetected)
        assertFalse(detector.analyze(ByteArray(100) { 55 }, 100).motionDetected)
        assertTrue(detector.analyze(ByteArray(100) { 90 }, 100).motionDetected)
    }

    @Test fun firstFrameOnlyInitializesBaseline() {
        assertFalse(MotionDetector().analyze(ByteArray(16) { 127.toByte() }, 100).motionDetected)
    }
}
