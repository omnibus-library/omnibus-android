package com.omnibus.omnibus.feature.libraries.switcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import kotlinx.coroutines.launch

/** Test tags for the switcher sheet. */
internal object LibrarySwitcherTestTags {
    const val SHEET = "library-switcher-sheet"
    const val TITLE = "library-switcher-title"
    const val LIST = "library-switcher-list"
}

/**
 * The width from which the switcher is a centred dialog rather than a bottom sheet: the shell's 600 dp rail
 * breakpoint, as the bottom-sheet rules' wide-window section requires.
 */
internal val SWITCHER_CENTERED_MIN_WIDTH: Dp = 600.dp

/** The widest the centred dialog gets. */
private val DialogMaxWidth = 560.dp

/** The corner radius of the centred dialog: Material's large container shape. */
private val DialogRadius = 28.dp

/** The tallest the bottom sheet gets, as a share of the window. */
private const val SHEET_MAX_HEIGHT = 0.9f

/** The tallest the centred dialog gets, as a share of the window. */
private const val DIALOG_MAX_HEIGHT = 0.8f

/**
 * The library switcher (C7): every saved library in the order they were added, with the open one checked. It is a
 * bottom sheet in a compact window and a centred dialog from [SWITCHER_CENTERED_MIN_WIDTH]. It needs nothing from
 * the shell, so it can be hosted anywhere.
 *
 * Tapping the open library closes it. Switching to another library arrives with #369.
 *
 * @param onDismiss Called once the sheet has closed: the open library tapped, a swipe down, the scrim or Back.
 */
@Composable
fun LibrarySwitcherSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibrarySwitcherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LibrarySwitcherLayout(
        libraries = uiState.libraries,
        onLibraryClick = ::closeOnCurrent,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * What a row tap does today: the open library closes the sheet, and any other library does nothing until switching
 * arrives with #369.
 */
internal fun closeOnCurrent(library: Library, close: () -> Unit) {
    if (library.isActive) close()
}

/**
 * The switcher without the ViewModel. Internal so tests can force either form with [centered].
 *
 * @param onLibraryClick Called with the tapped library and a function that closes the sheet (sliding it away first on
 * a phone) and then calls [onDismiss].
 * @param onDismiss Called once the sheet has closed.
 * @param centered Whether to show the dialog rather than the bottom sheet.
 */
@Composable
internal fun LibrarySwitcherLayout(
    libraries: List<Library>,
    onLibraryClick: (library: Library, close: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = LocalWindowInfo.current.containerDpSize.width >= SWITCHER_CENTERED_MIN_WIDTH,
) {
    if (centered) {
        LibrarySwitcherDialog(libraries, onLibraryClick, onDismiss, modifier)
    } else {
        LibrarySwitcherBottomSheet(libraries, onLibraryClick, onDismiss, modifier)
    }
}

/** The bottom sheet form. It opens at the height of its rows, up to 90% of the window, and the list scrolls past that. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySwitcherBottomSheet(
    libraries: List<Library>,
    onLibraryClick: (library: Library, close: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Hide first, then report, so the sheet slides away instead of vanishing when the caller drops it.
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(LibrarySwitcherTestTags.SHEET),
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        containerColor = palette.bg1,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        LibrarySwitcherContent(
            libraries = libraries,
            onLibraryClick = { library -> onLibraryClick(library, close) },
            modifier = Modifier.heightIn(max = windowHeight * SHEET_MAX_HEIGHT),
        )
    }
}

/** The centred dialog form. A dialog has no slide to wait for, so closing reports at once. */
@Composable
private fun LibrarySwitcherDialog(
    libraries: List<Library>,
    onLibraryClick: (library: Library, close: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    Dialog(onDismissRequest = onDismiss) {
        LibrarySwitcherDialogCard(
            libraries = libraries,
            onLibraryClick = { library -> onLibraryClick(library, onDismiss) },
            modifier = modifier
                .testTag(LibrarySwitcherTestTags.SHEET)
                .heightIn(max = windowHeight * DIALOG_MAX_HEIGHT),
        )
    }
}

/** The dialog's card: [LibrarySwitcherContent] on the sheet's background, up to [DialogMaxWidth] wide. */
@Composable
internal fun LibrarySwitcherDialogCard(
    libraries: List<Library>,
    onLibraryClick: (Library) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Surface(
        modifier = modifier.widthIn(max = DialogMaxWidth).fillMaxWidth(),
        shape = RoundedCornerShape(DialogRadius),
        color = palette.bg1,
        contentColor = palette.ink0,
        border = BorderStroke(0.5.dp, palette.line2),
    ) {
        LibrarySwitcherContent(libraries, onLibraryClick, Modifier.padding(top = Spacing.lg))
    }
}

/**
 * The switcher's body without the sheet window, so tests and screenshots can render it: the "Libraries" title, then
 * one [LibraryRow] per library. The list scrolls inside whatever height the window leaves it, under a fixed title.
 *
 * @param onLibraryClick Called with the library whose row was tapped.
 */
@Composable
internal fun LibrarySwitcherContent(
    libraries: List<Library>,
    onLibraryClick: (Library) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.libraries_switcher_title),
            style = displayTextStyle(26, palette.ink0),
            modifier = Modifier
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                .semantics { heading() }
                .testTag(LibrarySwitcherTestTags.TITLE),
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .testTag(LibrarySwitcherTestTags.LIST),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.xs,
                bottom = Spacing.xl,
            ),
        ) {
            items(libraries, key = { it.id }) { library ->
                LibraryRow(library, isCurrent = library.isActive, onClick = { onLibraryClick(library) })
            }
        }
    }
}
