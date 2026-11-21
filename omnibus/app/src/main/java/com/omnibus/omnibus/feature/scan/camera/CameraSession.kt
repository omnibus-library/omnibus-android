package com.omnibus.omnibus.feature.scan.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the scanner's CameraX session for the life of the scanner's nav entry, so a configuration change re-attaches the
 * preview rather than restarting the camera. Call its functions on the main thread.
 */
internal class CameraSession : ViewModel(), LifecycleOwner {
    // The owner is this ViewModel, not a Context, so the registry holds no Activity.
    @Suppress("StaticFieldLeak")
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry

    val preview: Preview = Preview.Builder().build()
    private val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build(),
    )
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /** The bound camera, or null until [start] has bound it. */
    var camera: Camera? by mutableStateOf(null)
        private set

    /** Whether the bound camera has a torch. */
    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    /** Binds the camera once, then opens it. */
    suspend fun start(context: Context) {
        if (camera == null) {
            val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            camera = provider.bindToLifecycle(this, selector, preview, analysis)
        }
        registry.currentState = Lifecycle.State.RESUMED
    }

    /** Closes the camera but keeps the binding, so a later [start] is quick. */
    fun stop() {
        registry.currentState = Lifecycle.State.CREATED
    }

    /** Reports the EAN-13 values of every analysed frame to [onFrame]. */
    fun analyze(onFrame: (List<String>) -> Unit) {
        analysis.setAnalyzer(executor, FrameAnalyzer(scanner, onFrame))
    }

    /** Reads each frame's barcodes with [scanner] and reports them. Closes each frame once its read has finished. */
    private class FrameAnalyzer(
        private val scanner: BarcodeScanner,
        private val onFrame: (List<String>) -> Unit,
    ) : ImageAnalysis.Analyzer {
        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val media = imageProxy.image
            if (media == null) {
                imageProxy.close()
                return
            }
            scanner.process(InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { codes -> onFrame(codes.mapNotNull { it.rawValue }) }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    /** Stops reporting frames. */
    fun stopAnalyzing() = analysis.clearAnalyzer()

    /** Turns the torch on or off. */
    fun setTorch(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    override fun onCleared() {
        analysis.clearAnalyzer()
        registry.currentState = Lifecycle.State.DESTROYED // CameraX unbinds a destroyed owner's use cases.
        scanner.close()
        executor.shutdown()
    }
}
