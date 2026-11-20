package com.omnibus.omnibus.feature.scan.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/** Whether the scanner can use a camera, and what the reader has answered about it. */
enum class CameraAvailability { Unknown, Ready, Denied, PermanentlyDenied, NoCamera }

/** What the scanner needs from the camera. Production is [rememberBarcodeCamera]; tests pass a fake. */
@Stable
interface BarcodeCamera {
    /** Whether the camera can be used, or why not. */
    val availability: CameraAvailability

    /** Whether the camera has a torch. */
    val hasFlash: Boolean

    /** Shows the system permission prompt. */
    fun requestPermission()

    /** Opens the app's system settings, where a permanently denied permission can be granted. */
    fun openSettings()

    /**
     * The live preview. Reports every analysed frame's EAN-13 values to [onFrame] on the main thread, an empty list
     * included. [frozen] dims and freezes the picture.
     */
    @Composable
    fun Preview(frozen: Boolean, torchOn: Boolean, onFrame: (List<String>) -> Unit, modifier: Modifier)
}
