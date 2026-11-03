package com.omnibus.omnibus.feature.bookdetail.more.delete

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.deletion.DeletableCopy
import com.omnibus.omnibus.data.deletion.DeletableFile
import com.omnibus.omnibus.data.deletion.DeletionManifest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The callbacks the delete sheet reports taps through. The overlay wires them to the ViewModel. */
internal class DeleteSheetCallbacks(
    val onToggleFile: (Long) -> Unit,
    val onToggleCopy: (Long) -> Unit,
    val onToggleAll: () -> Unit,
    val onContinue: () -> Unit,
    val onBack: () -> Unit,
    val onConfirm: () -> Unit,
    val onClose: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The Delete bottom sheet. While a delete is in flight, the sheet cannot be dismissed by a swipe, a scrim tap or Back.
 * Its body is [DeleteBookSheetContent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteBookSheet(
    state: DeleteBookUIState,
    book: Book,
    accent: Color,
    callbacks: DeleteSheetCallbacks,
) {
    val palette = OmnibusTheme.palette
    // The sheet state keeps the lambda it was created with, so it reads the latest value through this state.
    val deleting by rememberUpdatedState(state.deleting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !deleting },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!state.deleting) callbacks.onClose() },
        sheetState = sheetState,
        sheetGesturesEnabled = !state.deleting,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !state.deleting,
            shouldDismissOnClickOutside = !state.deleting,
        ),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = DeleteColors.SheetContainer,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        DeleteBookSheetContent(state = state, book = book, accent = accent, callbacks = callbacks)
    }
}

/**
 * The sheet's body, without the sheet window, so tests can render it directly. The title stays fixed and the body
 * scrolls. The title follows the step: the row's label while loading, the choose title, or the confirm heading.
 *
 * @param accent The book's accent, which colours the Select all text.
 */
@Composable
internal fun DeleteBookSheetContent(
    state: DeleteBookUIState,
    book: Book,
    accent: Color,
    callbacks: DeleteSheetCallbacks,
    modifier: Modifier = Modifier,
) {
    val sheet = state.sheet
    if (sheet == DeleteSheet.Closed) return
    val palette = OmnibusTheme.palette
    val title = when (sheet) {
        DeleteSheet.Closed -> ""
        DeleteSheet.Loading, DeleteSheet.LoadFailed -> rowLabel(
            if (hasFiles(book)) DeleteRowLabel.Files else DeleteRowLabel.Record,
        )
        is DeleteSheet.Choosing -> stringResource(R.string.book_detail_delete_choose_title, book.displayTitle)
        is DeleteSheet.Confirming -> headingText(
            confirmCopy(sheet.manifest, state.pickedFiles, state.pickedCopies).heading,
            book.displayTitle,
        )
    }
    Column(modifier.fillMaxWidth().testTag(DeleteTestTags.SHEET)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = displayTextStyle(28.sp, palette.ink0, FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .testTag(DeleteTestTags.TITLE)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            CloseButton(enabled = !state.deleting, onClick = callbacks.onClose)
        }
        when (sheet) {
            DeleteSheet.Closed -> Unit
            DeleteSheet.Loading -> Box(Modifier.fillMaxWidth().height(280.dp)) {
                LoadingStateView(
                    label = stringResource(R.string.book_detail_delete_loading),
                    modifier = Modifier.testTag(DeleteTestTags.LOADING),
                    background = DeleteColors.SheetContainer,
                )
            }
            DeleteSheet.LoadFailed -> Box(Modifier.fillMaxWidth().height(320.dp)) {
                ErrorStateView(
                    title = stringResource(R.string.book_detail_delete_load_error_title),
                    message = stringResource(R.string.book_detail_delete_load_error_message),
                    actionLabel = stringResource(R.string.book_detail_delete_retry),
                    onRetry = callbacks.onRetry,
                    modifier = Modifier.testTag(DeleteTestTags.ERROR),
                    background = DeleteColors.SheetContainer,
                )
            }
            is DeleteSheet.Choosing, is DeleteSheet.Confirming -> Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            ) {
                when (sheet) {
                    is DeleteSheet.Choosing -> ChooseBody(sheet.manifest, state, accent, callbacks)
                    is DeleteSheet.Confirming -> ConfirmBody(sheet.manifest, book, state, callbacks)
                    else -> Unit
                }
            }
        }
    }
}

/** The 44 dp close button at the title's end. It is dimmed and cannot be tapped while a delete is in flight. */
@Composable
private fun CloseButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .testTag(DeleteTestTags.CLOSE)
            .size(44.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = stringResource(R.string.book_detail_delete_close),
            tint = OmnibusTheme.palette.ink0,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** The choose step: the intro, the count and Select all, then one checkbox row per file and per copy, then the pills. */
@Composable
private fun ChooseBody(
    manifest: DeletionManifest,
    state: DeleteBookUIState,
    accent: Color,
    callbacks: DeleteSheetCallbacks,
) {
    val palette = OmnibusTheme.palette
    val hasCopies = manifest.copies.isNotEmpty()
    val picked = state.pickedFiles.size + state.pickedCopies.size
    Text(
        text = stringResource(
            if (hasCopies) R.string.book_detail_delete_intro_items else R.string.book_detail_delete_intro_files,
        ),
        style = uiTextStyle(15, FontWeight.Normal, DeleteColors.Body),
        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
    )
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = chooseSummaryText(chooseSummary(manifest)),
            style = uiTextStyle(15, FontWeight.SemiBold, palette.ink0),
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .testTag(DeleteTestTags.SELECT_ALL)
                .clickable(role = Role.Button, onClick = callbacks.onToggleAll),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = stringResource(
                    if (picked == manifest.itemCount) {
                        R.string.book_detail_delete_clear_selection
                    } else {
                        R.string.book_detail_delete_select_all
                    },
                ),
                style = uiTextStyle(15, FontWeight.SemiBold, accent),
            )
        }
    }
    manifest.files.forEach { file ->
        DeleteItemRow(
            title = file.label?.takeIf { it.isNotBlank() } ?: file.filename,
            detail = fileDetail(file),
            checked = file.id in state.pickedFiles,
            onToggle = { callbacks.onToggleFile(file.id) },
            tag = DeleteTestTags.fileRow(file.id),
        )
    }
    manifest.copies.forEach { copy ->
        DeleteItemRow(
            title = copyTitle(copy),
            detail = copyDetail(copy),
            checked = copy.id in state.pickedCopies,
            onToggle = { callbacks.onToggleCopy(copy.id) },
            tag = DeleteTestTags.copyRow(copy.id),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuietPill(
            label = stringResource(R.string.book_detail_delete_cancel),
            enabled = true,
            onClick = callbacks.onClose,
            tag = DeleteTestTags.CANCEL,
        )
        DangerPill(
            label = chooseActionText(chooseAction(picked, hasCopies)),
            enabled = picked > 0,
            onClick = callbacks.onContinue,
            tag = DeleteTestTags.CHOOSE_ACTION,
        )
    }
}

/** The confirm step: the body, the losses box when a whole-book delete takes data with it, the inline error, the pills. */
@Composable
private fun ConfirmBody(
    manifest: DeletionManifest,
    book: Book,
    state: DeleteBookUIState,
    callbacks: DeleteSheetCallbacks,
) {
    val palette = OmnibusTheme.palette
    val copy = confirmCopy(manifest, state.pickedFiles, state.pickedCopies)
    Text(
        text = bodyText(copy.body, book.displayTitle),
        style = uiTextStyle(17, FontWeight.Normal, palette.ink0),
        modifier = Modifier.padding(top = 4.dp).testTag(DeleteTestTags.CONFIRM_BODY),
    )
    lossesText(copy.losses)?.let { text ->
        Text(
            text = text,
            style = uiTextStyle(15, FontWeight.Normal, DeleteColors.Body),
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(DeleteColors.LossBox, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag(DeleteTestTags.LOSSES),
        )
    }
    state.deleteError?.let { error ->
        Text(
            text = failureText(error),
            style = uiTextStyle(14, FontWeight.Normal, palette.bad),
            modifier = Modifier.padding(top = 12.dp).testTag(DeleteTestTags.INLINE_ERROR),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (manifest.itemCount == 0) {
            QuietPill(
                label = stringResource(R.string.book_detail_delete_cancel),
                enabled = !state.deleting,
                onClick = callbacks.onClose,
                tag = DeleteTestTags.CANCEL,
            )
        } else {
            QuietPill(
                label = stringResource(R.string.book_detail_delete_back),
                enabled = !state.deleting,
                onClick = callbacks.onBack,
                tag = DeleteTestTags.BACK,
            )
        }
        DangerPill(
            label = if (state.deleting) {
                stringResource(R.string.book_detail_delete_deleting)
            } else {
                actionText(copy.action)
            },
            enabled = !state.deleting,
            onClick = callbacks.onConfirm,
            tag = DeleteTestTags.CONFIRM_ACTION,
        )
    }
}

/** One checkbox row: a 1 dp hairline above, a checkbox, and a title with its detail. The whole row is one toggle. */
@Composable
private fun DeleteItemRow(
    title: String,
    detail: String,
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String,
) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .drawBehind {
                drawLine(DeleteColors.RowRule, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = DeleteColors.CheckFill,
                uncheckedColor = palette.ink2,
                checkmarkColor = Color.White,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0))
            Text(detail, style = uiTextStyle(14, FontWeight.Normal, DeleteColors.Muted))
        }
    }
}

/** The file's format, with its size when the server reported one. */
@Composable
private fun fileDetail(file: DeletableFile): String {
    val format = file.format.uppercase()
    return if (file.sizeBytes > 0) {
        stringResource(
            R.string.book_detail_delete_file_detail,
            format,
            Formatter.formatShortFileSize(LocalContext.current, file.sizeBytes),
        )
    } else {
        format
    }
}

/** The copy's ISBN title, or the plain one when it has no ISBN. */
@Composable
private fun copyTitle(copy: DeletableCopy): String {
    val isbn = copy.isbn?.takeIf { it.isNotBlank() }
    return if (isbn != null) {
        stringResource(R.string.book_detail_delete_copy_title_isbn, isbn)
    } else {
        stringResource(R.string.book_detail_delete_copy_title)
    }
}

/** The copy's note, or the line that says it is only un-recorded. */
@Composable
private fun copyDetail(copy: DeletableCopy): String =
    copy.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.book_detail_delete_copy_detail)

/** The quiet pill: a Cancel or Back control, muted at 42 % when disabled. */
@Composable
private fun RowScope.QuietPill(label: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .testTag(tag)
            .clip(RoundedCornerShape(50))
            .background(DeleteColors.QuietFill)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = uiTextStyle(17, FontWeight.SemiBold, OmnibusTheme.palette.ink0), maxLines = 1)
    }
}

/** The danger pill: the delete action. Disabled, it is muted, and while a delete is in flight it reads "Deleting…". */
@Composable
private fun RowScope.DangerPill(label: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    val fill = if (enabled) DeleteColors.DangerFill else DeleteColors.QuietFill
    val ink = if (enabled) Color.White else DeleteColors.DisabledInk
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .testTag(tag)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .destructiveClick(enabled, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = uiTextStyle(17, FontWeight.SemiBold, ink), maxLines = 1)
    }
}

/** The opacity of a dimmed control, the same as the rest of the sheet's disabled states. */
private const val DISABLED_ALPHA = 0.42f
