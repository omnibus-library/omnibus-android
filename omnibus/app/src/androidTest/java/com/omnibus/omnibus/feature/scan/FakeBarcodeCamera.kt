package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.omnibus.omnibus.feature.scan.camera.BarcodeCamera
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability

/**
 * A [BarcodeCamera] that draws a plain box and lets a test set the availability and flash, push frames, and see what the
 * screen asked of the camera.
 */
class FakeBarcodeCamera(
    availability: CameraAvailability = CameraAvailability.Ready,
    hasFlash: Boolean = false,
) : BarcodeCamera {
    override var availability by mutableStateOf(availability)
    override var hasFlash by mutableStateOf(hasFlash)

    var requestPermissionCalls = 0
    var openSettingsCalls = 0

    /** The `frozen` value the preview was last composed with. */
    var lastFrozen: Boolean? = null

    /** The `torchOn` value the preview was last composed with. */
    var lastTorchOn: Boolean? = null

    /** The frame callback of the last composed preview, for a test to push frames through. */
    var onFrame: ((List<String>) -> Unit)? = null

    override fun requestPermission() {
        requestPermissionCalls++
    }

    override fun openSettings() {
        openSettingsCalls++
    }

    @Composable
    override fun Preview(frozen: Boolean, torchOn: Boolean, onFrame: (List<String>) -> Unit, modifier: Modifier) {
        SideEffect {
            lastFrozen = frozen
            lastTorchOn = torchOn
            this.onFrame = onFrame
        }
        Box(modifier.fillMaxSize().testTag("fake-camera"))
    }
}
