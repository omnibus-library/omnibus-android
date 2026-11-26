package com.omnibus.omnibus.feature.libraries.switcher

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** Test tags for the switcher sheet. */
internal object LibrarySwitcherTestTags {
    const val SHEET = "library-switcher-sheet"
    const val TITLE = "library-switcher-title"
    const val LIST = "library-switcher-list"
    const val MANAGE = "library-switcher-manage"
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
 * Tapping another library closes the sheet and switches to it. Tapping the open library only closes it.
 *
 * @param onDismiss Called once the sheet has closed: the open library tapped, a swipe down, the scrim or Back.
 * @param onManageLibraries Called after the sheet has closed, when "Manage libraries" was tapped.
 * @param onSwitchTo Called with the id of another library once the sheet has closed (#369).
 */
@Composable
fun LibrarySwitcherSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onManageLibraries: () -> Unit = {},
    onSwitchTo: (libraryId: String) -> Unit = {},
    viewModel: LibrarySwitcherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LibrarySwitcherLayout(
        libraries = uiState.libraries,
        onLibraryClick = { library, close -> switchOrClose(library, close, onSwitchTo) },
        onDismiss = onDismiss,
        modifier = modifier,
        onManageLibraries = onManageLibraries,
    )
}

/**
 * What a row tap does for the open library: it only closes the sheet. The sheet itself uses [switchOrClose]; this stays
 * for tests of the open row alone.
 */
internal fun closeOnCurrent(library: Library, close: () -> Unit) {
    if (library.isActive) close()
}

/**
 * What a row tap does (#369): closes the sheet, then, for any library but the open one, switches to it. The open
 * library only closes the sheet.
 */
internal fun switchOrClose(library: Library, close: () -> Unit, onSwitchTo: (libraryId: String) -> Unit) {
    close()
    if (!library.isActive) onSwitchTo(library.id)
}

/**
 * The switcher without the ViewModel. Internal so tests can force either form with [centered].
 *
 * @param onLibraryClick Called with the tapped library and a function that closes the sheet (sliding it away first on
 * a phone) and then calls [onDismiss].
 * @param onDismiss Called once the sheet has closed.
 * @param centered Whether to show the dialog rather than the bottom sheet.
 * @param onManageLibraries Called after the sheet has closed, when "Manage libraries" was tapped.
 */
@Composable
internal fun LibrarySwitcherLayout(
    libraries: List<Library>,
    onLibraryClick: (library: Library, close: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = LocalWindowInfo.current.containerDpSize.width >= SWITCHER_CENTERED_MIN_WIDTH,
    onManageLibraries: () -> Unit = {},
) {
    if (centered) {
        LibrarySwitcherDialog(libraries, onLibraryClick, onDismiss, modifier, onManageLibraries)
    } else {
        LibrarySwitcherBottomSheet(libraries, onLibraryClick, onDismiss, modifier, onManageLibraries)
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
    onManageLibraries: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Hide first, then run the rest, so the sheet slides away instead of vanishing when the caller drops it.
    val hideThen: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) then() }
    }
    val close: () -> Unit = { hideThen(onDismiss) }
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
            onManageLibraries = { hideThen { onDismiss(); onManageLibraries() } },
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
    onManageLibraries: () -> Unit,
) {
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    Dialog(onDismissRequest = onDismiss) {
        LibrarySwitcherDialogCard(
            libraries = libraries,
            onLibraryClick = { library -> onLibraryClick(library, onDismiss) },
            modifier = modifier
                .testTag(LibrarySwitcherTestTags.SHEET)
                .heightIn(max = windowHeight * DIALOG_MAX_HEIGHT),
            onManageLibraries = { onDismiss(); onManageLibraries() },
        )
    }
}

/** The dialog's card: [LibrarySwitcherContent] on the sheet's background, up to [DialogMaxWidth] wide. */
@Composable
internal fun LibrarySwitcherDialogCard(
    libraries: List<Library>,
    onLibraryClick: (Library) -> Unit,
    modifier: Modifier = Modifier,
    onManageLibraries: () -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    Surface(
        modifier = modifier.widthIn(max = DialogMaxWidth).fillMaxWidth(),
        shape = RoundedCornerShape(DialogRadius),
        color = palette.bg1,
        contentColor = palette.ink0,
        border = BorderStroke(0.5.dp, palette.line2),
    ) {
        LibrarySwitcherContent(
            libraries = libraries,
            onLibraryClick = onLibraryClick,
            modifier = Modifier.padding(top = Spacing.lg),
            onManageLibraries = onManageLibraries,
        )
    }
}

/**
 * The switcher's body without the sheet window, so tests and screenshots can render it: the "Libraries" title, then
 * one [LibraryRow] per library. The list scrolls inside whatever height the window leaves it, under a fixed title.
 *
 * @param onLibraryClick Called with the library whose row was tapped.
 * @param onManageLibraries Called when the "Manage libraries" action is tapped.
 */
@Composable
internal fun LibrarySwitcherContent(
    libraries: List<Library>,
    onLibraryClick: (Library) -> Unit,
    modifier: Modifier = Modifier,
    onManageLibraries: () -> Unit = {},
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
                bottom = Spacing.sm,
            ),
        ) {
            items(libraries, key = { it.id }) { library ->
                LibraryRow(library, isCurrent = library.isActive, onClick = { onLibraryClick(library) })
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = Spacing.screen), thickness = 0.5.dp, color = palette.line)
        SwitcherAction(
            icon = R.drawable.ic_libraries_manage,
            label = stringResource(R.string.libraries_manage_sheet_action),
            testTag = LibrarySwitcherTestTags.MANAGE,
            onClick = onManageLibraries,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )
    }
}

/** A full-width action row under the list: a gear icon and its label, one TalkBack button. */
@Composable
private fun SwitcherAction(
    @DrawableRes icon: Int,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag)
            .padding(horizontal = Spacing.screen, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The 40dp box lines the label up with the library names above it.
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = palette.ink2, modifier = Modifier.size(20.dp))
        }
        Text(label, style = uiTextStyle(16, FontWeight.Medium, palette.ink0))
    }
}
