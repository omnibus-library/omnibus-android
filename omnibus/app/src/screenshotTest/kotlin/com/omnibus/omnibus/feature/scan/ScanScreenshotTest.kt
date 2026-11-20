package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.feature.scan.camera.BarcodeCamera
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability
import com.omnibus.omnibus.screenshot.Shot
import androidx.compose.ui.tooling.preview.PreviewLightDark

/** A camera that draws a black preview, so a reference shows the stage without any live picture. */
@Stable
internal class PreviewCamera(override val availability: CameraAvailability) : BarcodeCamera {
    override val hasFlash: Boolean = true

    override fun requestPermission() = Unit

    override fun openSettings() = Unit

    @Composable
    override fun Preview(frozen: Boolean, torchOn: Boolean, onFrame: (List<String>) -> Unit, modifier: Modifier) {
        Box(modifier.fillMaxSize().background(Color.Black))
    }
}

private val previewBook = ScanBook(
    uuid = "preview-book",
    title = "The Overstory",
    authors = listOf("Richard Powers"),
    coverUrl = null,
    hasPhysical = false,
    hasFiles = true,
    isbn = "9780393636529",
)

/** A not-an-ISBN notice over the live camera. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanViewfinderRejected() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Viewfinder(rejected = RejectedNotice(value = "0123456789012", announce = true)),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = true,
        )
    }
}

/** Typing an ISBN with a bad check digit. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanManualError() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(stage = ScanStage.Manual(text = "978-0-671-68357-4", submitted = true)),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = true,
        )
    }
}

/** The camera is permanently denied, so Open settings is offered beside Enter ISBN. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanCameraUnavailable() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(),
            camera = PreviewCamera(CameraAvailability.PermanentlyDenied),
            actions = ScanActions(),
            wide = false,
            reducedMotion = true,
        )
    }
}

/** A book already in the library as a digital copy, ready for its print copy to be checked in. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanOutcomeInLibrary() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Outcome(ScanOutcome.InLibraryUnowned(previewBook), "9780393636529", FoundVia.Scan),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = false,
        )
    }
}

/** The success with the halo and confetti at their first frame. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanSuccessCheckedIn() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Success(
                    title = previewBook.title,
                    cover = SuccessCover.Library(previewBook),
                    bookUuid = previewBook.uuid,
                    kind = SuccessKind.CheckedIn,
                ),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = false,
        )
    }
}

/** The same success under reduced motion: a static ring and no confetti. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanSuccessReducedMotion() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Success(
                    title = previewBook.title,
                    cover = SuccessCover.Library(previewBook),
                    bookUuid = previewBook.uuid,
                    kind = SuccessKind.CheckedIn,
                ),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = true,
        )
    }
}
