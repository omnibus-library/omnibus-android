package com.omnibus.omnibus.feature.library.addbooks

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** Test tags for the Add books sheet. */
internal object AddBooksTestTags {
    const val SHEET = "add-books-sheet"
    const val TITLE = "add-books-title"
    const val DONE = "add-books-done"
    fun option(kind: AddBooksOptionKind) = "add-books-option-${kind.name}"
}

/** The description and chevron colour. iOS uses ink3, which reads 2.9:1 on bg1 in the dark theme, so the sheet uses ink2 (5.2:1 dark, 5.0:1 light). */
internal val OmnibusPalette.addBooksSecondaryInk: Color
    get() = ink2

/** The opacity of a row that is shown but can't be tapped. */
private const val DISABLED_ALPHA = 0.4f

/**
 * The width from which Add books is a centered dialog rather than a bottom sheet. It is the shell's own 600 dp rail
 * breakpoint (`ShellNavigationLayout.kt`), so the sheet and the navigation change together. Material keeps bottom sheets
 * for compact windows, where a 640 dp sheet would float at the bottom of a wide, tall window.
 */
internal val ADD_BOOKS_CENTERED_MIN_WIDTH: Dp = 600.dp

/** The width of the centered dialog. */
private val DialogWidth = 448.dp

/** The corner radius of the centered dialog: Material's large container shape. */
private val DialogRadius = 28.dp

/**
 * The Add books sheet: a bottom sheet in a compact window, and from [ADD_BOOKS_CENTERED_MIN_WIDTH] up a [centered] dialog
 * with the same content. Either closes through [onClose] or reports an option through [onSelect].
 *
 * @param onSelect Called with the kind of an option the user tapped, after the sheet has hidden.
 * @param onClose Called after the sheet has hidden for Done, the scrim, a swipe down or Back.
 * @param centered Whether to show the dialog rather than the bottom sheet. Tests pass it to force either layout.
 */
@Composable
internal fun AddBooksSheet(
    options: List<AddBooksOption>,
    onSelect: (AddBooksOptionKind) -> Unit,
    onClose: () -> Unit,
    centered: Boolean = LocalWindowInfo.current.containerDpSize.width >= ADD_BOOKS_CENTERED_MIN_WIDTH,
) {
    if (centered) {
        AddBooksDialog(options, onSelect, onClose)
    } else {
        AddBooksBottomSheet(options, onSelect, onClose)
    }
}

/**
 * The Add books bottom sheet. Opens at half height and drags to full. Done and an option hide the sheet first, then report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBooksBottomSheet(
    options: List<AddBooksOption>,
    onSelect: (AddBooksOptionKind) -> Unit,
    onClose: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    // Hide first, then report, so the sheet slides away instead of vanishing when the caller drops it.
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) action() }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = Modifier.testTag(AddBooksTestTags.SHEET),
        sheetState = sheetState,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        containerColor = palette.bg0,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        AddBooksSheetContent(
            options = options,
            onSelect = { kind -> hideThen { onSelect(kind) } },
            onDone = { hideThen(onClose) },
            // Filling the sheet's height gives it a half-height detent even though three rows are short (R8).
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

/** The Add books dialog: [AddBooksDialogCard] centered over a scrim. A dialog has no slide to wait for, so taps report at once. */
@Composable
private fun AddBooksDialog(
    options: List<AddBooksOption>,
    onSelect: (AddBooksOptionKind) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        AddBooksDialogCard(options, onSelect, onDone = onClose, modifier = Modifier.testTag(AddBooksTestTags.SHEET))
    }
}

/**
 * The dialog's card: [AddBooksSheetContent] on the sheet's background, [DialogWidth] wide and as tall as its rows.
 *
 * @param onSelect Called with the kind of an enabled option the user tapped.
 * @param onDone Called when the user taps Done.
 */
@Composable
internal fun AddBooksDialogCard(
    options: List<AddBooksOption>,
    onSelect: (AddBooksOptionKind) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Surface(
        modifier = modifier.width(DialogWidth),
        shape = RoundedCornerShape(DialogRadius),
        color = palette.bg0,
        contentColor = palette.ink0,
        border = BorderStroke(0.5.dp, palette.line2),
    ) {
        AddBooksSheetContent(options, onSelect, onDone, Modifier.padding(top = Spacing.md))
    }
}

/**
 * The sheet's body without the sheet window, so tests and previews can render it.
 *
 * @param onSelect Called with the kind of an enabled option the user tapped.
 * @param onDone Called when the user taps Done.
 */
@Composable
internal fun AddBooksSheetContent(
    options: List<AddBooksOption>,
    onSelect: (AddBooksOptionKind) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.add_books_title),
                style = displayTextStyle(26, palette.ink0),
                modifier = Modifier.weight(1f).semantics { heading() }.testTag(AddBooksTestTags.TITLE),
            )
            TextLinkButton(
                text = stringResource(R.string.add_books_done),
                color = palette.accent,
                size = 15,
                onClick = onDone,
                modifier = Modifier.testTag(AddBooksTestTags.DONE),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            options.forEach { option -> AddBooksOptionRow(option, onClick = { onSelect(option.kind) }) }
        }
    }
}

/** One Add books row: an accent icon, a title and a description, with a chevron. A disabled row is dimmed and can't be tapped. */
@Composable
private fun AddBooksOptionRow(option: AddBooksOption, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.md)
    val copy = option.optionCopy()
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (option.enabled) 1f else DISABLED_ALPHA)
            .testTag(AddBooksTestTags.option(option.kind))
            .clip(shape)
            .background(palette.bg1)
            .border(0.5.dp, palette.line2, shape)
            .clickable(enabled = option.enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(copy.icon), contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(copy.title), style = uiTextStyle(15.5.sp, FontWeight.Medium, palette.ink0))
            Text(stringResource(copy.description), style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.addBooksSecondaryInk))
        }
        Icon(
            painterResource(R.drawable.ic_reader_chevron_right),
            contentDescription = null,
            tint = palette.addBooksSecondaryInk,
            modifier = Modifier.size(12.dp),
        )
    }
}

private data class OptionCopy(
    @param:DrawableRes val icon: Int,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
)

/** The icon and copy for the row. The offline description replaces the normal one while Upload is disabled. */
private fun AddBooksOption.optionCopy(): OptionCopy = when (kind) {
    AddBooksOptionKind.Upload -> OptionCopy(
        R.drawable.ic_add_books_upload,
        R.string.add_books_upload_title,
        if (enabled) R.string.add_books_upload_description else R.string.add_books_upload_offline,
    )
    AddBooksOptionKind.Scan -> OptionCopy(R.drawable.ic_add_books_scan, R.string.add_books_scan_title, R.string.add_books_scan_description)
    AddBooksOptionKind.Wishlist -> OptionCopy(
        R.drawable.ic_add_books_wishlist,
        R.string.add_books_wishlist_title,
        R.string.add_books_wishlist_description,
    )
}

@PreviewLightDark
@Composable
private fun AddBooksSheetContentPreview() {
    OmnibusTheme {
        AddBooksSheetContent(addBooksOptions(canUpload = true, isOffline = false), {}, {}, Modifier.background(OmnibusTheme.palette.bg0))
    }
}

@PreviewLightDark
@Composable
private fun AddBooksSheetContentOfflinePreview() {
    OmnibusTheme {
        AddBooksSheetContent(addBooksOptions(canUpload = true, isOffline = true), {}, {}, Modifier.background(OmnibusTheme.palette.bg0))
    }
}

@PreviewLightDark
@Composable
private fun AddBooksSheetContentNoUploadPreview() {
    OmnibusTheme {
        AddBooksSheetContent(addBooksOptions(canUpload = false, isOffline = false), {}, {}, Modifier.background(OmnibusTheme.palette.bg0))
    }
}
