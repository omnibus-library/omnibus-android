package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** The sheet's muted text colour (the design's #c8c4be). */
private val SheetMuted = Color(0xFFC8C4BE)

/** The sheet's container, the design's #15120f. */
private val SheetContainer = Color(0xFF15120F)

/** The hairline above each row, rgba(247, 244, 239, .1). */
private val RowRule = Color(0xFFF7F4EF).copy(alpha = 0.10f)

/** The opacity of a row that is offline or waits on another save. */
private const val DimmedAlpha = 0.5f

/** The opacity of the Close button while a Kindle send holds the sheet open. */
private const val DisabledCloseAlpha = 0.42f

/** The minimum height of a send row (Kindle, Kobo). */
private val SendRowMinHeight: Dp = 66.dp

/** The minimum height of a download row (ebook, audiobook). */
private val SaveRowMinHeight: Dp = 60.dp

/**
 * The Export bottom sheet (#118). While a Kindle send is in flight the sheet cannot be dismissed: the swipe, the scrim,
 * Back and Close are all locked, so the send's result always lands on a sheet the reader can see.
 *
 * @param sending Whether the Kindle send is in flight, which locks the sheet.
 * @param accentInk The colour of the row actions, from the book's cover tone (see [exportAccentInk]).
 * @param onClose Called when the sheet is closed by the user, after it has hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportSheet(
    model: ExportSheetModel,
    saveError: ExportSaveError?,
    sending: Boolean,
    accentInk: Color,
    onSend: () -> Unit,
    onSave: (ExportSave) -> Unit,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val locked = rememberUpdatedState(sending)
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !locked.value },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!locked.value) onClose() },
        sheetState = sheetState,
        sheetGesturesEnabled = !sending,
        // Left at their defaults: the sheet reads these once when its window is created, so flipping them while a
        // send runs would leave Back dead afterwards. `onDismissRequest` and the inner BackHandler do the locking.
        properties = ModalBottomSheetProperties(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = SheetContainer,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        ExportSheetContent(
            model = model,
            saveError = saveError,
            sending = sending,
            accentInk = accentInk,
            onSend = onSend,
            onSave = onSave,
            onSettings = onSettings,
            onOpen = onOpen,
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onClose()
                }
            },
        )
    }
}

/**
 * The sheet's body, without the sheet window, so tests can drive it. The title and Close stay fixed; the rows scroll.
 * Rows report taps through the callbacks; the caller owns the state.
 *
 * @param sending Whether a Kindle send is in flight: Close is disabled and Back is swallowed.
 */
@Composable
internal fun ExportSheetContent(
    model: ExportSheetModel,
    saveError: ExportSaveError?,
    sending: Boolean,
    accentInk: Color,
    onSend: () -> Unit,
    onSave: (ExportSave) -> Unit,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val opens = stringResource(R.string.book_detail_export_opens_share_sheet)
    BackHandler(enabled = sending) {}
    Column(modifier.fillMaxWidth().testTag(ExportTestTags.SHEET).padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.book_detail_export_title),
                style = displayTextStyle(28.sp, palette.ink0, FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag(ExportTestTags.TITLE),
            )
            CloseButton(enabled = !sending, onClick = onClose)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            if (model.showsSendSection) {
                SectionTitle(R.string.book_detail_export_send_section, ExportTestTags.SEND_SECTION, top = 4.dp)
                model.kindle?.let { KindleRowItem(it, accentInk, onSend, onSettings, onOpen) }
                model.kobo?.let { KoboRowItem(it, accentInk, onSave) }
            }
            if (model.showsSaveSection) {
                SectionTitle(R.string.book_detail_export_save_section, ExportTestTags.SAVE_SECTION, top = 18.dp)
                model.ebook?.let { row ->
                    DownloadRowItem(
                        tag = ExportTestTags.EBOOK,
                        title = stringResource(R.string.book_detail_export_download_format, row.format.orEmpty()),
                        meta = ebookMeta(exportSize(row.sizeBytes), opens),
                        row = row,
                        onSave = { onSave(ExportSave.Ebook) },
                    )
                }
                model.audio?.let { row ->
                    DownloadRowItem(
                        tag = ExportTestTags.AUDIO,
                        title = stringResource(R.string.book_detail_export_download_audiobook),
                        meta = audioMeta(row.format, exportSize(row.sizeBytes), opens),
                        row = row,
                        onSave = { onSave(ExportSave.Audio) },
                    )
                }
            }
            saveError?.let { error ->
                Text(
                    text = stringResource(saveErrorText(error)),
                    style = uiTextStyle(14, FontWeight.Normal, palette.bad),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(ExportTestTags.ERROR),
                )
            }
        }
    }
}

/**
 * The Kindle row's copy and action for one phase. [onClick] is ignored when the row is disabled.
 */
private class KindleCopy(
    val subtitle: String,
    val subtitleColor: Color,
    val end: String?,
    val endColor: Color,
    val onClick: () -> Unit,
)

/** The Kindle row. Its copy and action follow the gate and the send's phase, which the model already resolved. */
@Composable
private fun KindleRowItem(
    row: KindleRow,
    accentInk: Color,
    onSend: () -> Unit,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
) {
    val sends = stringResource(
        if (row.sendsEpub) R.string.book_detail_export_kindle_sends_epub else R.string.book_detail_export_kindle_sends_book,
    )
    val copy = when (row.state) {
        KindleRowState.Ready -> KindleCopy(
            subtitle = sends,
            subtitleColor = SheetMuted,
            end = stringResource(R.string.book_detail_export_kindle_send),
            endColor = accentInk,
            onClick = onSend,
        )
        KindleRowState.Sending -> KindleCopy(
            subtitle = sends,
            subtitleColor = SheetMuted,
            end = stringResource(R.string.book_detail_export_kindle_sending),
            endColor = SheetMuted,
            onClick = {},
        )
        KindleRowState.Sent -> KindleCopy(
            subtitle = stringResource(R.string.book_detail_export_kindle_sent_meta),
            subtitleColor = SheetMuted,
            end = stringResource(R.string.book_detail_export_kindle_sent),
            endColor = accentInk,
            onClick = {},
        )
        KindleRowState.Failed -> {
            val failure = row.failure ?: KindlePhase.Failed(KindleFailure.Unknown)
            KindleCopy(
                subtitle = failure.serverMessage ?: stringResource(kindleFailureText(failure.reason)),
                subtitleColor = OmnibusTheme.palette.bad,
                end = stringResource(R.string.book_detail_export_kindle_retry),
                endColor = accentInk,
                onClick = onSend,
            )
        }
        KindleRowState.NoAddress -> KindleCopy(
            subtitle = stringResource(R.string.book_detail_export_kindle_no_address),
            subtitleColor = SheetMuted,
            end = stringResource(R.string.book_detail_export_kindle_settings),
            endColor = accentInk,
            onClick = onSettings,
        )
        KindleRowState.Oversize -> KindleCopy(
            subtitle = stringResource(R.string.book_detail_export_kindle_oversize),
            subtitleColor = SheetMuted,
            end = stringResource(R.string.book_detail_export_kindle_open),
            endColor = accentInk,
            onClick = onOpen,
        )
        KindleRowState.Offline -> KindleCopy(
            subtitle = stringResource(R.string.book_detail_export_needs_connection),
            subtitleColor = SheetMuted,
            end = null,
            endColor = accentInk,
            onClick = {},
        )
    }
    ExportRow(
        tag = ExportTestTags.KINDLE,
        title = stringResource(R.string.book_detail_export_kindle),
        subtitle = copy.subtitle,
        subtitleColor = copy.subtitleColor,
        enabled = row.enabled,
        dimmed = row.dimmed,
        minHeight = SendRowMinHeight,
        onClick = copy.onClick,
        live = true,
    ) {
        copy.end?.let { EndLabel(it, copy.endColor) }
    }
}

/** The Kobo row: a KEPUB saved to copy onto a Kobo. Offline it says so and has no action. */
@Composable
private fun KoboRowItem(row: SaveRow, accentInk: Color, onSave: (ExportSave) -> Unit) {
    val saving = stringResource(R.string.book_detail_export_saving)
    ExportRow(
        tag = ExportTestTags.KOBO,
        title = stringResource(R.string.book_detail_export_kobo),
        subtitle = if (row.offline) {
            stringResource(R.string.book_detail_export_needs_connection)
        } else {
            stringResource(R.string.book_detail_export_kobo_meta)
        },
        subtitleColor = SheetMuted,
        enabled = row.enabled,
        dimmed = row.dimmed,
        minHeight = SendRowMinHeight,
        onClick = { onSave(ExportSave.Kobo) },
        stateText = if (row.busy) saving else null,
    ) {
        if (!row.offline) {
            if (row.busy) {
                // The state is spoken once, as the row's state description.
                Text(
                    text = saving,
                    style = uiTextStyle(15, FontWeight.SemiBold, SheetMuted),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            } else {
                EndLabel(stringResource(R.string.book_detail_export_save), accentInk)
            }
        }
    }
}

/**
 * An ebook or audiobook row: the title, the size and the opens copy, with a share icon. While it saves, a spinner
 * replaces the icon. Offline it says so and has no action.
 */
@Composable
private fun DownloadRowItem(tag: String, title: String, meta: String, row: SaveRow, onSave: () -> Unit) {
    val saving = stringResource(R.string.book_detail_export_saving)
    ExportRow(
        tag = tag,
        title = title,
        subtitle = if (row.offline) stringResource(R.string.book_detail_export_needs_connection) else meta,
        subtitleColor = SheetMuted,
        enabled = row.enabled,
        dimmed = row.dimmed,
        minHeight = SaveRowMinHeight,
        onClick = onSave,
        stateText = if (row.busy) saving else null,
    ) {
        if (!row.offline) {
            if (row.busy) {
                CircularProgressIndicator(
                    color = SheetMuted,
                    strokeWidth = 2.2.dp,
                    modifier = Modifier.size(20.dp).testTag(ExportTestTags.PROGRESS),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_book_detail_more_export),
                    contentDescription = null,
                    tint = SheetMuted,
                    modifier = Modifier.size(20.dp).testTag(ExportTestTags.SHARE_ICON),
                )
            }
        }
    }
}

/**
 * One tappable row: a hairline above it, a title and a subtitle, and a trailing action. The row is one button for
 * TalkBack; [live] makes its text a polite live region, and [stateText] is spoken as its state.
 *
 * @param stateText The row's state for TalkBack, such as "Saving…", or null.
 */
@Composable
private fun ExportRow(
    tag: String,
    title: String,
    subtitle: String,
    subtitleColor: Color,
    enabled: Boolean,
    dimmed: Boolean,
    minHeight: Dp,
    onClick: () -> Unit,
    live: Boolean = false,
    stateText: String? = null,
    trailing: @Composable () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .testTag(tag)
            .topRule()
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .alpha(if (dimmed) DimmedAlpha else 1f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (live) liveRegion = LiveRegionMode.Polite
                if (stateText != null) stateDescription = stateText
            }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            )
            Text(
                text = subtitle,
                style = uiTextStyle(14, FontWeight.Normal, subtitleColor),
            )
        }
        trailing()
    }
}

/** A row's action label at the row's end. */
@Composable
private fun EndLabel(text: String, color: Color) {
    Text(
        text = text,
        style = uiTextStyle(15, FontWeight.SemiBold, color),
    )
}

/** A section's heading, such as "Send to a device". */
@Composable
private fun SectionTitle(titleRes: Int, tag: String, top: Dp) {
    Text(
        text = stringResource(titleRes),
        style = uiTextStyle(15, FontWeight.SemiBold, OmnibusTheme.palette.ink0),
        modifier = Modifier
            .padding(top = top, bottom = 6.dp)
            .semantics { heading() }
            .testTag(tag),
    )
}

/** The 44 dp close button at the title's end. Disabled while a Kindle send holds the sheet. */
@Composable
private fun CloseButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .testTag(ExportTestTags.CLOSE)
            .size(44.dp)
            .clip(CircleShape)
            .alpha(if (enabled) 1f else DisabledCloseAlpha)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = stringResource(R.string.book_detail_export_close),
            tint = OmnibusTheme.palette.ink0,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The sentence for why a Kindle send failed. */
private fun kindleFailureText(reason: KindleFailure): Int = when (reason) {
    KindleFailure.Undelivered -> R.string.book_detail_export_kindle_failed_undelivered
    KindleFailure.Unconfirmed -> R.string.book_detail_export_kindle_failed_unconfirmed
    KindleFailure.NotConfigured -> R.string.book_detail_export_kindle_failed_not_configured
    KindleFailure.NoAddress -> R.string.book_detail_export_kindle_no_address
    KindleFailure.NotFound -> R.string.book_detail_export_kindle_failed_not_found
    KindleFailure.Unreachable -> R.string.book_detail_export_kindle_failed_unreachable
    KindleFailure.Unknown -> R.string.book_detail_export_kindle_failed_unknown
}

/** The sentence for why a download failed. */
private fun saveErrorText(error: ExportSaveError): Int = when (error) {
    ExportSaveError.Forbidden -> R.string.book_detail_export_error_forbidden
    ExportSaveError.NotFound -> R.string.book_detail_export_error_not_found
    ExportSaveError.Unreachable -> R.string.book_detail_export_error_unreachable
    ExportSaveError.NoSpace -> R.string.book_detail_export_error_no_space
    ExportSaveError.Failed -> R.string.book_detail_export_error_failed
}

/** The end-label ink of the export actions: the book's cover tone at the design's a(0.8) lightness. */
internal fun exportAccentInk(book: Book): Color = book.coverIdentity().tone.let { Oklch(0.80, it.c, it.h).toColor() }

/** A hairline across the top of a row. */
private fun Modifier.topRule(): Modifier = drawBehind {
    drawLine(RowRule, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}
