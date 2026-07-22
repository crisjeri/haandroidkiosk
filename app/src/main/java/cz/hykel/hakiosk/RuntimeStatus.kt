package cz.hykel.hakiosk

object RuntimeStatus {
    @Volatile var networkAvailable: Boolean = false
    @Volatile var cameraState: String = "Neaktivní"
    @Volatile var serviceRunning: Boolean = false
    @Volatile var lastMotionElapsedRealtime: Long = 0L
    @Volatile var motionValue: Double = 0.0
    @Volatile var lastWebViewError: String? = null
}
