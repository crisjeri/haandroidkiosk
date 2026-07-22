package cz.hykel.hakiosk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import kotlin.math.abs

class CameraController(
    private val context: Context,
    private val desiredFps: () -> Int,
    private val listener: Listener,
) {
    interface Listener {
        fun onFrame(luma: ByteArray, width: Int, height: Int, timestampMillis: Long)
        fun onCameraState(state: String)
        fun onCameraError(message: String, error: Throwable? = null)
    }

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastAnalyzedMillis = 0L

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        if (cameraDevice != null || thread != null) return
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listener.onCameraError("Oprávnění kamery nebylo uděleno")
            return
        }
        val workerThread = HandlerThread("HaKioskCamera").also { it.start() }
        thread = workerThread
        handler = Handler(workerThread.looper)
        try {
            val cameraId = findFrontCamera() ?: error("Přední kamera nebyla nalezena")
            listener.onCameraState("Otevírání")
            cameraManager.openCamera(cameraId, cameraStateCallback, handler)
        } catch (error: Exception) {
            listener.onCameraError("Kameru nelze otevřít", error)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        runCatching { captureSession?.close() }
        runCatching { cameraDevice?.close() }
        runCatching { imageReader?.close() }
        captureSession = null
        cameraDevice = null
        imageReader = null
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
        listener.onCameraState("Neaktivní")
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCaptureSession(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            listener.onCameraError("Kamera byla odpojena")
            stop()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            listener.onCameraError("Chyba kamery: $error")
            stop()
        }
    }

    private fun findFrontCamera(): String? = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
            CameraCharacteristics.LENS_FACING_FRONT
    }

    private fun createCaptureSession(camera: CameraDevice) {
        try {
            val characteristics = cameraManager.getCameraCharacteristics(camera.id)
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888)
                .orEmpty()
            val size = chooseSize(sizes)
            val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ source -> analyzeLatest(source) }, handler)
            camera.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            chooseFpsRange(characteristics)?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                        }.build()
                        session.setRepeatingRequest(request, null, handler)
                        listener.onCameraState("Aktivní ${size.width}×${size.height}")
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        listener.onCameraError("Konfigurace kamery selhala")
                        stop()
                    }
                },
                handler,
            )
        } catch (error: Exception) {
            listener.onCameraError("Nelze vytvořit relaci kamery", error)
            stop()
        }
    }

    private fun analyzeLatest(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        image.use {
            val now = android.os.SystemClock.elapsedRealtime()
            val interval = 1_000L / desiredFps().coerceIn(1, 3)
            if (now - lastAnalyzedMillis < interval) return
            lastAnalyzedMillis = now
            val (luma, outputSize) = downsampleLuma(it, 4)
            listener.onFrame(luma, outputSize.width, outputSize.height, now)
        }
    }

    private fun downsampleLuma(image: Image, step: Int): Pair<ByteArray, Size> {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val outputWidth = (image.width + step - 1) / step
        val outputHeight = (image.height + step - 1) / step
        val output = ByteArray(outputWidth * outputHeight)
        var outputIndex = 0
        for (y in 0 until image.height step step) {
            val rowOffset = y * plane.rowStride
            for (x in 0 until image.width step step) {
                output[outputIndex++] = buffer.get(rowOffset + x * plane.pixelStride)
            }
        }
        return output to Size(outputWidth, outputHeight)
    }

    private fun chooseSize(sizes: Array<out Size>): Size {
        if (sizes.isEmpty()) return Size(320, 240)
        val suitable = sizes.filter { it.width <= 640 && it.height <= 480 }
        return (suitable.ifEmpty { sizes.toList() }).minBy { abs(it.width * it.height - 320 * 240) }
    }

    private fun chooseFpsRange(characteristics: CameraCharacteristics): Range<Int>? {
        return characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filter { it.upper >= 5 }
            ?.minByOrNull { it.upper * 100 + it.lower }
    }
}
