package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.scan.camera.BarcodeCamera
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability
import com.omnibus.omnibus.feature.scan.camera.rememberBarcodeCamera
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/** From this width the scanner uses two panes and a centred column, as the shell's rail does (`RailBreakpoint`). */
internal val SCAN_WIDE_MIN_WIDTH: Dp = 600.dp

/** The widest a centred viewfinder, outcome or search column gets on a wide window (`AdaptiveFormLayout`'s precedent). */
internal val SCAN_COLUMN_MAX_WIDTH: Dp = 480.dp

/** Test tags of the scanner's parts. */
internal object ScanTestTags {
    const val TITLE = "scan-title"
    const val VIEWFINDER = "scan-viewfinder"
    const val TORCH = "scan-torch"
    const val NOTICE = "scan-notice"
    const val BANNER = "scan-banner"
    const val CLOSE = "scan-close"
    const val BACK = "scan-back"
    const val CARD = "scan-card"
    const val ACTIONS = "scan-actions"
    const val CELEBRATION = "scan-celebration"
    const val WRITE_ERROR = "scan-write-error"
}

/** The screen's callbacks, one per thing the reader can do. Each defaults to doing nothing. */
data class ScanActions(
    val onClose: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onBarcodes: (List<String>) -> Unit = {},
    val onTorchToggled: () -> Unit = {},
    val onEnterIsbn: () -> Unit = {},
    val onIsbnChanged: (String) -> Unit = {},
    val onFindBook: () -> Unit = {},
    val onScanInstead: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onNoteChanged: (String) -> Unit = {},
    val onCheckIn: () -> Unit = {},
    val onAddPhysical: () -> Unit = {},
    val onAddToWishlist: () -> Unit = {},
    val onScanAnother: () -> Unit = {},
    val onViewBook: (String) -> Unit = {},
)

/** The barcode scanner. Shows one stage at a time, and leaves through [onClose] or [onViewBook]. */
@Composable
fun ScanScreen(
    onClose: () -> Unit,
    onViewBook: (bookUuid: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanScreenViewModel = hiltViewModel(),
    camera: BarcodeCamera = rememberBarcodeCamera(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    }
    ScanScreenContent(
        uiState = uiState,
        camera = camera,
        actions = ScanActions(
            onClose = onClose,
            onBack = viewModel::backTapped,
            onBarcodes = viewModel::barcodesSeen,
            onTorchToggled = viewModel::torchToggled,
            onEnterIsbn = viewModel::enterIsbnTapped,
            onIsbnChanged = viewModel::isbnChanged,
            onFindBook = viewModel::findBookTapped,
            onScanInstead = viewModel::scanInsteadTapped,
            onTryAgain = viewModel::tryAgainTapped,
            onNoteChanged = viewModel::noteChanged,
            onCheckIn = viewModel::checkInTapped,
            onAddPhysical = viewModel::addPhysicalTapped,
            onAddToWishlist = viewModel::addToWishlistTapped,
            onScanAnother = viewModel::scanAnotherTapped,
            onViewBook = onViewBook,
        ),
        modifier = modifier,
    )
}

/** The scanner's stages, stateless so every stage can be tested and previewed with a hand-built [uiState]. */
@Composable
internal fun ScanScreenContent(
    uiState: ScanUIState,
    camera: BarcodeCamera,
    actions: ScanActions,
    modifier: Modifier = Modifier,
    wide: Boolean = LocalWindowInfo.current.containerDpSize.width >= SCAN_WIDE_MIN_WIDTH,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    LaunchedEffect(camera.availability) {
        if (camera.availability == CameraAvailability.Unknown) camera.requestPermission()
    }
    Box(modifier.fillMaxSize().background(palette.bg0).safeDrawingPadding()) {
        when (val stage = uiState.stage) {
            is ScanStage.Viewfinder -> ViewfinderOrUnavailable(
                resolvingIsbn = null,
                lookupFailed = stage.lookupFailed,
                rejected = stage.rejected,
                uiState = uiState,
                camera = camera,
                actions = actions,
                wide = wide,
            )

            is ScanStage.Resolving -> ViewfinderOrUnavailable(
                resolvingIsbn = stage.isbn,
                lookupFailed = null,
                rejected = null,
                uiState = uiState,
                camera = camera,
                actions = actions,
                wide = wide,
            )

            is ScanStage.Manual -> ScanManualStage(stage, actions, wide)
            is ScanStage.Outcome -> ScanOutcomeStage(
                outcome = stage.outcome,
                note = stage.note,
                writing = stage.writing,
                writeFailed = stage.writeFailed,
                actions = actions,
                wide = wide,
            )

            is ScanStage.Success -> ScanSuccessStage(stage, actions, wide, reducedMotion)
        }
    }
}

@Composable
private fun ViewfinderOrUnavailable(
    resolvingIsbn: String?,
    lookupFailed: LookupFailure?,
    rejected: RejectedNotice?,
    uiState: ScanUIState,
    camera: BarcodeCamera,
    actions: ScanActions,
    wide: Boolean,
) {
    when (camera.availability) {
        CameraAvailability.Ready -> ScanViewfinderStage(
            resolvingIsbn = resolvingIsbn,
            lookupFailed = lookupFailed,
            rejected = rejected,
            torchOn = uiState.torchOn,
            camera = camera,
            actions = actions,
            wide = wide,
        )

        CameraAvailability.Unknown -> Unit
        else -> ScanCameraUnavailableStage(camera, actions)
    }
}
