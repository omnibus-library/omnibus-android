package com.omnibus.omnibus.feature.reader.note

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.highlights.HIGHLIGHT_NOTE_MAX_LENGTH
import com.omnibus.omnibus.ui.components.InlineError
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.text.NumberFormat

/** The test tags of [ReaderNoteScreen]. */
internal object ReaderNoteScreenTags {
    const val ROOT = "reader_note_root"
    const val CLOSE = "reader_note_close"
    const val TITLE = "reader_note_title"
    const val SAVE = "reader_note_save"
    const val QUOTE = "reader_note_quote"
    const val FIELD = "reader_note_field"
    const val COUNTER = "reader_note_counter"
    const val ERROR = "reader_note_error"
    const val DISCARD = "reader_note_discard"
    const val KEEP_WRITING = "reader_note_keep_writing"
}

/**
 * The full-screen note writer, drawn over the whole reader on the app theme, so it covers the page and the docked mini
 * player. It takes every touch so none reaches the page beneath. It handles no back press; the host does.
 *
 * @param state The note screen's state, from the reader's ViewModel.
 * @param onTextChanged The reader typed in the field.
 * @param onSave Save was tapped.
 * @param onClose The close button was tapped.
 * @param onDiscardConfirmed Discard was chosen in the "Discard note?" dialog.
 * @param onDiscardCancelled Keep writing was chosen, or the dialog was dismissed.
 */
@Composable
fun ReaderNoteScreen(
    state: NoteComposerState,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onDiscardConfirmed: () -> Unit,
    onDiscardCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    NoteSystemBars(light = palette.bg0.luminance() > 0.5f)

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    // Purely visual, so it stays out of the ViewModel.
    var focused by remember { mutableStateOf(false) }

    val savingLabel = stringResource(R.string.reader_note_saving)
    val fieldLabel = stringResource(R.string.reader_note_field_label)
    val errorText = stringResource(R.string.reader_note_save_failed)

    Box(
        modifier
            .fillMaxSize()
            .background(palette.bg0)
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }
            .semantics { isTraversalGroup = true }
            .testTag(ReaderNoteScreenTags.ROOT),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = Spacing.xs, end = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag(ReaderNoteScreenTags.CLOSE)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_reader_close),
                        contentDescription = stringResource(R.string.reader_note_close),
                        tint = palette.ink0,
                    )
                }
                Text(
                    text = stringResource(R.string.reader_note_title_new),
                    style = uiTextStyle(17.sp, FontWeight.SemiBold, palette.ink0),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.xs)
                        .semantics { heading() }
                        .testTag(ReaderNoteScreenTags.TITLE),
                )
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 68.dp)
                        .clickable(enabled = state.canSave, role = Role.Button, onClick = onSave)
                        .semantics { if (state.saving) stateDescription = savingLabel }
                        .testTag(ReaderNoteScreenTags.SAVE),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (state.canSave) palette.accent else palette.ink0.copy(alpha = 0.10f))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = palette.ink0.copy(alpha = 0.58f),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.reader_note_save),
                                style = uiTextStyle(
                                    16.sp,
                                    FontWeight.SemiBold,
                                    if (state.canSave) palette.accentInk else palette.ink0.copy(alpha = 0.58f),
                                ),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = palette.line)

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    if (!state.quote.isNullOrBlank()) {
                        Text(
                            text = state.quote.trim(),
                            style = displayTextStyle(19.sp, palette.ink1).copy(lineHeight = 27.sp),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRoundRect(
                                        color = palette.accent,
                                        size = Size(2.5.dp.toPx(), size.height),
                                        cornerRadius = CornerRadius(2.dp.toPx()),
                                    )
                                }
                                .padding(start = 14.dp)
                                .testTag(ReaderNoteScreenTags.QUOTE),
                        )
                    }

                    BasicTextField(
                        value = state.text,
                        onValueChange = onTextChanged,
                        readOnly = state.saving,
                        textStyle = uiTextStyle(16.sp, FontWeight.Normal, palette.ink0).copy(lineHeight = 23.sp),
                        cursorBrush = SolidColor(palette.accent),
                        minLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { focused = it.isFocused }
                            .semantics { contentDescription = fieldLabel }
                            .testTag(ReaderNoteScreenTags.FIELD),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.bg1)
                                    .border(
                                        1.dp,
                                        if (focused) palette.accent else palette.line2,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                if (state.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.reader_note_placeholder),
                                        style = uiTextStyle(16.sp, FontWeight.Normal, palette.ink2),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
            }
            // Pinned under the scroll, so a long note never pushes the counter or the save error off screen.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            bottom = if (state.showsCounter || state.failed) Spacing.lg else 0.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (state.showsCounter) {
                        val count = noteLength(state.text)
                        val format = NumberFormat.getIntegerInstance()
                        Text(
                            text = stringResource(
                                R.string.reader_note_counter,
                                format.format(count),
                                format.format(HIGHLIGHT_NOTE_MAX_LENGTH),
                            ),
                            style = monoTextStyle(
                                12.sp,
                                if (count >= HIGHLIGHT_NOTE_MAX_LENGTH) palette.badText else palette.ink2,
                            ),
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag(ReaderNoteScreenTags.COUNTER),
                        )
                    }

                    InlineError(
                        message = if (state.failed) errorText else null,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(ReaderNoteScreenTags.ERROR),
                    )
                }
            }
        }
    }

    if (state.confirmingDiscard) {
        ReaderNoteDiscardDialog(onDiscard = onDiscardConfirmed, onKeepWriting = onDiscardCancelled)
    }
}

/** The "Discard note?" dialog: Discard and Keep writing, with no body. Dismissing it keeps writing. */
@Composable
private fun ReaderNoteDiscardDialog(onDiscard: () -> Unit, onKeepWriting: () -> Unit) {
    val palette = OmnibusTheme.palette
    AlertDialog(
        onDismissRequest = onKeepWriting,
        title = { Text(stringResource(R.string.reader_note_discard_title)) },
        confirmButton = {
            TextButton(
                onClick = onDiscard,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ReaderNoteScreenTags.DISCARD),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.bad),
            ) {
                Text(stringResource(R.string.reader_note_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onKeepWriting,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ReaderNoteScreenTags.KEEP_WRITING),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
            ) {
                Text(stringResource(R.string.reader_note_discard_keep))
            }
        },
        containerColor = palette.bg2,
        titleContentColor = palette.ink0,
    )
}

/**
 * Sets the system bars' icons for the note screen's own ground, [light] meaning dark icons, and puts the reader's back
 * when the screen leaves composition.
 */
@Composable
private fun NoteSystemBars(light: Boolean) {
    val window = if (LocalInspectionMode.current) null else LocalActivity.current?.window
    if (window != null) {
        DisposableEffect(window, light) {
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            val previousStatus = bars.isAppearanceLightStatusBars
            val previousNavigation = bars.isAppearanceLightNavigationBars
            bars.isAppearanceLightStatusBars = light
            bars.isAppearanceLightNavigationBars = light
            onDispose {
                bars.isAppearanceLightStatusBars = previousStatus
                bars.isAppearanceLightNavigationBars = previousNavigation
            }
        }
    }
}
