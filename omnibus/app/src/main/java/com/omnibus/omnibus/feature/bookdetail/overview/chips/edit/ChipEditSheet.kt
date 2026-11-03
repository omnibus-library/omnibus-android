package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.feature.bookdetail.overview.chips.ChipHeight
import com.omnibus.omnibus.feature.bookdetail.overview.chips.ChipTouchSize
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The sheet's container, the same fixed dark as the Add to shelf sheet, in both themes. */
private val ChipSheetContainer = Color(0xFF15120F)

/** The field's fill and border, the same values as the other sheet fields. */
private val FieldFill = Color(0xFF1B1713)
private val FieldBorder = Color(0xFFF7F5F1).copy(alpha = 0.28f)

/** The hairline above each suggestion and create row. */
private val RowRule = Color(0xFFF7F5F1).copy(alpha = 0.10f)

/**
 * The editor's body: the title, hint, the book's current chips (each removes itself when tapped), the field, the
 * suggestions and the add-as-new row, and the notes. It holds no state; [state] and the callbacks come from the
 * caller. The caller makes the colours Atrium (dark), so this reads [OmnibusTheme.palette] as it is given.
 *
 * @param inlineError A failed save's message, shown in the warn colour when the sheet is open.
 * @param autoFocus Whether the field asks for focus when it first appears.
 */
@Composable
internal fun ChipEditSheetContent(
    state: ChipEditUIState,
    kind: MetadataListKind,
    bookTitle: String,
    inlineError: String?,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    onEntryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val palette = OmnibusTheme.palette
    val isGenres = kind == MetadataListKind.Genres
    val values = state.valuesOf(kind)
    val full = values.size >= MAX_CHIPS
    val pool = (state.pools[kind] as? PoolState.Loaded)?.entries.orEmpty()
    val typed = state.entry.trim()

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
            .testTag(ChipEditTestTags.SHEET),
    ) {
        Text(
            text = stringResource(
                R.string.book_detail_chipedit_title,
                stringResource(if (isGenres) R.string.book_detail_chips_genres else R.string.book_detail_chips_tags),
                bookTitle,
            ),
            style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics { heading() }
                .testTag(ChipEditTestTags.TITLE),
        )
        Text(
            text = stringResource(R.string.book_detail_chipedit_hint),
            style = uiTextStyle(12, FontWeight.Normal, palette.ink2),
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        if (values.isEmpty()) {
            Text(
                text = stringResource(R.string.book_detail_chipedit_empty),
                style = uiTextStyle(13, FontWeight.Normal, palette.ink2),
                modifier = Modifier.testTag(ChipEditTestTags.EMPTY),
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                values.forEach { value ->
                    RemovableChip(value, enabled = state.isOnline, onClick = { onRemove(value) })
                }
            }
        }

        EntryField(
            text = state.entry,
            placeholder = stringResource(if (isGenres) R.string.book_detail_chipedit_field_genres else R.string.book_detail_chipedit_field_tags),
            enabled = state.isOnline && !full,
            autoFocus = autoFocus,
            onValueChange = { onEntryChange(it.take(MAX_CHIP_LENGTH)) },
            onDone = { if (typed.isNotEmpty()) onAdd(typed) },
            modifier = Modifier.padding(top = 14.dp),
        )

        if (!full) {
            SuggestionPool.filtered(pool, values, state.entry).forEach { entry ->
                SuggestionRow(
                    name = entry.name,
                    count = entry.count,
                    enabled = state.isOnline,
                    onClick = { onAdd(entry.name) },
                )
            }
            if (SuggestionPool.showsCreateRow(pool, values, state.entry)) {
                CreateRow(
                    text = stringResource(
                        if (isGenres) R.string.book_detail_chipedit_create_genre else R.string.book_detail_chipedit_create_tag,
                        typed,
                    ),
                    enabled = state.isOnline,
                    onClick = { onAdd(typed) },
                )
            }
        }

        if (!state.isOnline) {
            Text(
                text = stringResource(R.string.book_detail_chipedit_offline),
                style = uiTextStyle(12, FontWeight.Normal, palette.ink2),
                modifier = Modifier.padding(top = 12.dp).testTag(ChipEditTestTags.OFFLINE),
            )
        }
        if (full) {
            Text(
                text = stringResource(R.string.book_detail_chipedit_full),
                style = uiTextStyle(12, FontWeight.Normal, palette.ink2),
                modifier = Modifier.padding(top = 12.dp).testTag(ChipEditTestTags.FULL),
            )
        }
        if (inlineError != null) {
            Text(
                text = inlineError,
                style = uiTextStyle(12, FontWeight.Normal, palette.warn),
                modifier = Modifier.padding(top = 12.dp).testTag(ChipEditTestTags.ERROR),
            )
        }
    }
}

/**
 * The bottom sheet that edits [kind]'s list. It opens half height and can be dragged to full. Dismissing it, by the
 * scrim, a drag or back, calls [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChipEditSheet(
    state: ChipEditUIState,
    kind: MetadataListKind,
    bookTitle: String,
    inlineError: String?,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    onEntryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
) {
    val palette = OmnibusPalette.Atrium
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = ChipSheetContainer,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        OmnibusTheme(darkTheme = true) {
            ChipEditSheetContent(
                state = state,
                kind = kind,
                bookTitle = bookTitle,
                inlineError = inlineError,
                onRemove = onRemove,
                onAdd = onAdd,
                onEntryChange = onEntryChange,
                autoFocus = autoFocus,
            )
        }
    }
}

/** One current chip with a trailing ×. The whole chip is the remove button. */
@Composable
private fun RemovableChip(value: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    val description = stringResource(R.string.book_detail_chipedit_remove, value)
    Box(
        Modifier
            .heightIn(min = ChipTouchSize)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .testTag(ChipEditTestTags.CHIP),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = ChipHeight)
                .border(1.dp, palette.ink0.copy(alpha = 0.24f), CircleShape)
                .padding(start = 13.dp, end = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = value,
                style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CloseGlyph(color = palette.ink0.copy(alpha = 0.7f))
        }
    }
}

/** A 9 dp × drawn as two round-capped lines. */
@Composable
private fun CloseGlyph(color: Color) {
    Canvas(Modifier.size(9.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(w, h), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w, 0f), Offset(0f, h), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** The sheet's one-line field, styled like the app's other fields and capped at [MAX_CHIP_LENGTH]. */
@Composable
private fun EntryField(
    text: String,
    placeholder: String,
    enabled: Boolean,
    autoFocus: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val focus = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(Unit) {
        if (autoFocus && enabled) focus.requestFocus()
    }
    BasicTextField(
        value = text,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = uiTextStyle(16, FontWeight.Normal, palette.ink0),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRequester(focus)
            .alpha(if (enabled) 1f else 0.4f)
            .testTag(ChipEditTestTags.FIELD),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(text = placeholder, style = uiTextStyle(16, FontWeight.Normal, palette.ink2))
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
                container = {
                    Box(
                        Modifier
                            .background(FieldFill, RoundedCornerShape(12.dp))
                            .border(1.dp, FieldBorder, RoundedCornerShape(12.dp)),
                    )
                },
            )
        },
    )
}

/** A suggestion: the name on the left and its book count on the right. */
@Composable
private fun SuggestionRow(name: String, count: Long, enabled: Boolean, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .drawBehind { drawLine(RowRule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(ChipEditTestTags.SUGGESTION),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = uiTextStyle(14, FontWeight.Normal, palette.ink1),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(R.plurals.book_detail_chipedit_book_count, count.toInt(), count.toInt()),
            style = uiTextStyle(11, FontWeight.Normal, palette.ink2),
        )
    }
}

/** The add-as-new row for text that matches no value. */
@Composable
private fun CreateRow(text: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .drawBehind { drawLine(RowRule, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(ChipEditTestTags.CREATE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = uiTextStyle(14, FontWeight.Medium, palette.ink0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A bound, signed-in editor state for a preview, with [pool] as the loaded suggestions for genres. */
private fun previewState(
    genres: List<String> = emptyList(),
    entry: String = "",
    isOnline: Boolean = true,
    pool: List<String> = emptyList(),
) = ChipEditUIState(
    canEdit = true,
    isOnline = isOnline,
    bound = true,
    genres = genres,
    entry = entry,
    pools = if (pool.isEmpty()) emptyMap()
    else mapOf(MetadataListKind.Genres to PoolState.Loaded(pool.map { VocabularyEntry(it, 1) })),
)

/** The sheet's content on the sheet's own dark container, as the sheet draws it in both themes. */
@Composable
private fun ChipEditSheetPreview(state: ChipEditUIState, inlineError: String? = null) {
    OmnibusTheme {
        Box(Modifier.background(ChipSheetContainer).padding(16.dp)) {
            OmnibusTheme(darkTheme = true) {
                ChipEditSheetContent(
                    state = state,
                    kind = MetadataListKind.Genres,
                    bookTitle = "The Left Hand of Darkness",
                    inlineError = inlineError,
                    onRemove = {},
                    onAdd = {},
                    onEntryChange = {},
                )
            }
        }
    }
}

private val PreviewGenres = listOf("Science Fiction", "Epic", "Noir")
private val PreviewPool = listOf("Historical Fiction", "History", "Hist-Mystery", "Noir", "Space Opera")

@PreviewLightDark
@Composable
private fun ChipEditSheetDefaultPreview() {
    ChipEditSheetPreview(previewState(genres = PreviewGenres, pool = PreviewPool))
}

@PreviewLightDark
@Composable
private fun ChipEditSheetTypingPreview() {
    ChipEditSheetPreview(previewState(genres = PreviewGenres, entry = "hist", pool = PreviewPool))
}

@PreviewLightDark
@Composable
private fun ChipEditSheetEmptyPreview() {
    ChipEditSheetPreview(previewState())
}

@PreviewLightDark
@Composable
private fun ChipEditSheetOfflinePreview() {
    ChipEditSheetPreview(previewState(genres = PreviewGenres, isOnline = false, pool = PreviewPool))
}

@PreviewLightDark
@Composable
private fun ChipEditSheetFullPreview() {
    ChipEditSheetPreview(previewState(genres = List(MAX_CHIPS) { "Genre ${it + 1}" }))
}

@PreviewLightDark
@Composable
private fun ChipEditSheetFailedPreview() {
    ChipEditSheetPreview(
        previewState(genres = PreviewGenres),
        inlineError = stringResource(R.string.book_detail_chipedit_failed_genres),
    )
}
