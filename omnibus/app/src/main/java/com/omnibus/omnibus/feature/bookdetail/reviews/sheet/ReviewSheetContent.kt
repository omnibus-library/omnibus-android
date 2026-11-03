package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.stage.washShowsImage
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Every callback the sheet body raises. The ViewModel decides what each one does. */
@Immutable
internal data class ReviewSheetActions(
    val onCancel: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onRatingChange: (Float?) -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onSpoilerChange: (Boolean) -> Unit = {},
    val onExpandNotes: () -> Unit = {},
    val onOpenPicker: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
)

/** The sheet container colour, which the wash fades into. */
private val SheetContainer = Color(0xFF15120F)

/** The fill of the review and notes fields. */
private val FieldGround = Color(0xFF0C0A08)

/** The secondary text colour. */
private val Muted = Color(0xFFC8C4BE)

/** The solid border of the review field, when it is not focused. */
private val FieldBorder = Color(0xFFF7F4EF).copy(alpha = 0.18f)

/** The dashed border of the collapsed private notes row. */
private val DashedBorder = Color(0xFFF7F4EF).copy(alpha = 0.26f)

/** The dashed border of the expanded private notes field. */
private val ExpandedDashedBorder = Color(0xFFF7F4EF).copy(alpha = 0.30f)

/** The bar above the keyboard accessory row. */
private val AccessoryGround = Color(0xFF201C18)

/** The height of the cover wash at the top of the sheet. */
private val WashHeight = 240.dp

/** The fraction of the cover wash's width and height the blurred cover fills. */
private const val WASH_WIDTH_SCALE = 1.4f
private const val WASH_HEIGHT_SCALE = 1.8f

/**
 * The review sheet's body, without any ViewModel: a header with Cancel, the title and Save; an optional failure banner;
 * the cover, title and large stars, or the compact stars while typing; the conflict note; the Review field; and, when
 * not typing, the From highlights row, the spoiler switch and the private notes. While the review field has focus the
 * cover, stars and notes hide, and an accessory row with Highlights and Done sits above the keyboard.
 *
 * @param state What the sheet shows.
 * @param book The book being reviewed, for its cover, title and author.
 * @param actions Every callback the body raises.
 * @param reducedMotion Whether the system asks for no animation.
 */
@Composable
internal fun ReviewSheetContent(
    state: ReviewSheetUIState,
    book: Book,
    actions: ReviewSheetActions,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    val identity = remember(book) { book.coverIdentity() }
    val tone = identity.tone
    val accentText = Oklch(0.80, tone.c, tone.h).toColor()
    val accentFill = Oklch(0.52, tone.c, tone.h).toColor()
    val accentSave = Oklch(0.44, tone.c, tone.h).toColor()
    val focus = LocalFocusManager.current
    val scroll = rememberScrollState()
    var typing by remember { mutableStateOf(false) }
    val showsImage = washShowsImage(book.coverUrl != null, Build.VERSION.SDK_INT)

    Box(modifier.fillMaxSize().background(SheetContainer).testTag(ReviewSheetTestTags.SHEET)) {
        // The blurred cover fades into the sheet at the foot of the wash.
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(WashHeight).clipToBounds()) {
            if (showsImage) {
                BookCover(
                    identity = identity,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = WASH_WIDTH_SCALE
                            scaleY = WASH_HEIGHT_SCALE
                        }
                        .blur(28.dp)
                        .alpha(0.62f),
                    size = CoverSize.Md,
                    cornerRadius = 0.dp,
                    keepsAspect = false,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(0f to SheetContainer.copy(alpha = 0.3f), 0.96f to SheetContainer),
                    ),
            )
        }

        Column(Modifier.fillMaxSize()) {
            Header(
                state = state,
                saveEnabled = state.saveEnabled,
                accentSave = accentSave,
                onCancel = actions.onCancel,
                onSave = actions.onSave,
            )

            state.failure?.let { failure ->
                Text(
                    text = stringResource(
                        if (failure.offline) {
                            R.string.book_detail_review_sheet_save_failed_offline
                        } else {
                            R.string.book_detail_review_sheet_save_failed
                        },
                    ),
                    style = uiTextStyle(13.sp, FontWeight.Normal, palette.bad),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.bad.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag(ReviewSheetTestTags.FAILURE),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (typing) Modifier else Modifier.verticalScroll(scroll))
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (typing) 8.dp else 34.dp)
                    .testTag(ReviewSheetTestTags.BODY),
            ) {
                if (!typing) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp)
                            .size(58.dp, 86.dp)
                            .testTag(ReviewSheetTestTags.COVER),
                    ) {
                        BookCover(
                            identity = identity,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = -2.5f }
                                .coverShadow(cornerRadius = 5.dp),
                            cornerRadius = 5.dp,
                            keepsAspect = false,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = book.displayTitle,
                        style = displayTextStyle(24.sp, palette.ink0, FontWeight.SemiBold),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag(ReviewSheetTestTags.BOOK_TITLE),
                    )
                    Text(
                        text = book.authorDisplay,
                        style = uiTextStyle(14.sp, FontWeight.Normal, Muted),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    ReviewSheetStars(
                        rating = state.shown.rating,
                        onRatingChange = actions.onRatingChange,
                        tint = accentText,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(14.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                    ReviewSheetCompactStars(
                        rating = state.shown.rating,
                        onRatingChange = actions.onRatingChange,
                        tint = accentText,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (state.conflicts.isNotEmpty()) {
                    ConflictNote(state.conflicts)
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.book_detail_review_sheet_review_label),
                        style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0),
                    )
                    Text(
                        text = stringResource(R.string.book_detail_review_sheet_review_audience),
                        style = uiTextStyle(13.sp, FontWeight.Normal, Muted),
                    )
                }

                // The field stays at the same place in the composition in both layouts, so typing keeps its focus.
                ReviewTextField(
                    value = state.shown.text.orEmpty(),
                    onValueChange = actions.onTextChange,
                    accentText = accentText,
                    typing = typing,
                    onFocusChanged = { typing = it },
                    fieldModifier = if (typing) {
                        Modifier.weight(1f).heightIn(min = 120.dp)
                    } else {
                        Modifier.heightIn(min = 112.dp)
                    },
                )

                if (!typing) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClick = actions.onOpenPicker)
                            .testTag(ReviewSheetTestTags.FROM_HIGHLIGHTS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = RibbonIcon,
                            contentDescription = null,
                            tint = accentText,
                            modifier = Modifier.size(12.dp, 20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.book_detail_review_sheet_from_highlights),
                            style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = state.shown.spoiler,
                                role = Role.Switch,
                                onValueChange = actions.onSpoilerChange,
                            )
                            .testTag(ReviewSheetTestTags.SPOILER),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.book_detail_review_sheet_spoiler),
                            style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink0),
                        )
                        Spacer(Modifier.size(12.dp))
                        Switch(
                            checked = state.shown.spoiler,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(checkedTrackColor = accentFill),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrivateNotes(
                        expanded = state.notesExpanded,
                        notes = state.shown.notes.orEmpty(),
                        onExpand = actions.onExpandNotes,
                        onNotesChange = actions.onNotesChange,
                    )
                }
            }

            if (typing) {
                AccessoryRow(
                    accentText = accentText,
                    onOpenPicker = actions.onOpenPicker,
                    onDone = { focus.clearFocus() },
                )
            }
        }

        if (state.confirmingDiscard) {
            ReviewDiscardDialog(onDiscard = actions.onDiscard, onKeepEditing = actions.onKeepEditing)
        }
    }
}

/** The header: Cancel at the start, the title in the middle, and Save at the end, in a row 48 dp high. */
@Composable
private fun Header(
    state: ReviewSheetUIState,
    saveEnabled: Boolean,
    accentSave: Color,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onCancel,
            enabled = !state.saving,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag(ReviewSheetTestTags.CANCEL),
            colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
        ) {
            Text(
                text = stringResource(R.string.book_detail_review_sheet_cancel),
                style = uiTextStyle(17.sp, FontWeight.Medium, palette.ink0),
            )
        }
        Text(
            text = stringResource(
                if (state.title == ReviewSheetTitle.Edit) {
                    R.string.book_detail_review_sheet_title_edit
                } else {
                    R.string.book_detail_review_sheet_title_write
                },
            ),
            style = displayTextStyle(21.sp, palette.ink0, FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
                .testTag(ReviewSheetTestTags.TITLE),
        )
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 68.dp)
                .clickable(enabled = saveEnabled, role = Role.Button, onClick = onSave)
                .testTag(ReviewSheetTestTags.SAVE),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (saveEnabled) accentSave else palette.ink0.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.book_detail_review_sheet_save),
                    style = uiTextStyle(
                        16.sp,
                        FontWeight.SemiBold,
                        if (saveEnabled) Color.White else palette.ink0.copy(alpha = 0.58f),
                    ),
                )
            }
        }
    }
}

/** The conflict note above the review field: the one field's copy, or the several-fields copy. */
@Composable
private fun ConflictNote(conflicts: List<ReviewField>) {
    val palette = OmnibusTheme.palette
    val text = if (conflicts.size > 1) {
        stringResource(R.string.book_detail_review_sheet_conflict_several)
    } else {
        stringResource(
            when (conflicts.first()) {
                ReviewField.Rating -> R.string.book_detail_review_sheet_conflict_rating
                ReviewField.Text -> R.string.book_detail_review_sheet_conflict_text
                ReviewField.Notes -> R.string.book_detail_review_sheet_conflict_notes
                ReviewField.Spoiler -> R.string.book_detail_review_sheet_conflict_spoiler
            },
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.warn.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = InfoIcon,
            contentDescription = null,
            tint = palette.warn,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = text,
            style = uiTextStyle(13.sp, FontWeight.Normal, palette.warn),
            modifier = Modifier.testTag(ReviewSheetTestTags.CONFLICT),
        )
    }
}

/** The Review field: plain Markdown source, bound to the ViewModel's text on every keystroke. */
@Composable
private fun ReviewTextField(
    value: String,
    onValueChange: (String) -> Unit,
    accentText: Color,
    typing: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    fieldModifier: Modifier,
) {
    val palette = OmnibusTheme.palette
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = uiTextStyle(16.sp, FontWeight.Normal, palette.ink0).copy(lineHeight = 23.sp),
        cursorBrush = SolidColor(accentText),
        modifier = fieldModifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .testTag(ReviewSheetTestTags.FIELD),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(FieldGround)
                    .border(1.dp, if (typing) accentText else FieldBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.book_detail_review_sheet_review_placeholder),
                        style = uiTextStyle(16.sp, FontWeight.Normal, Muted.copy(alpha = 0.7f)),
                    )
                }
                inner()
            }
        },
    )
}

/**
 * The private notes: a dashed row that expands into a labelled dashed field. It starts expanded when the review has
 * notes, which the ViewModel decides.
 */
@Composable
private fun PrivateNotes(
    expanded: Boolean,
    notes: String,
    onExpand: () -> Unit,
    onNotesChange: (String) -> Unit,
) {
    val palette = OmnibusTheme.palette
    if (!expanded) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .dashedBorder(DashedBorder, 12.dp)
                .clickable(role = Role.Button, onClick = onExpand)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag(ReviewSheetTestTags.NOTES_ROW),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = LockIcon,
                contentDescription = null,
                tint = palette.ink1,
                modifier = Modifier.size(15.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.book_detail_review_sheet_notes_label),
                    style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0),
                )
                Text(
                    text = stringResource(R.string.book_detail_review_sheet_notes_hint),
                    style = uiTextStyle(13.sp, FontWeight.Normal, Muted),
                )
            }
            Icon(
                imageVector = PlusIcon,
                contentDescription = null,
                tint = palette.ink1,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = LockIcon,
                    contentDescription = null,
                    tint = palette.ink1,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = stringResource(R.string.book_detail_review_sheet_notes_label),
                    style = uiTextStyle(13.sp, FontWeight.SemiBold, palette.ink1),
                )
                Text(
                    text = stringResource(R.string.book_detail_review_sheet_notes_hint),
                    style = uiTextStyle(13.sp, FontWeight.Normal, Muted),
                )
            }
            BasicTextField(
                value = notes,
                onValueChange = onNotesChange,
                textStyle = uiTextStyle(16.sp, FontWeight.Normal, palette.ink0).copy(lineHeight = 23.sp),
                cursorBrush = SolidColor(palette.ink0),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 82.dp)
                    .testTag(ReviewSheetTestTags.NOTES_FIELD),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FieldGround)
                            .dashedBorder(ExpandedDashedBorder, 12.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (notes.isEmpty()) {
                            Text(
                                text = stringResource(R.string.book_detail_review_sheet_notes_placeholder),
                                style = uiTextStyle(16.sp, FontWeight.Normal, Muted.copy(alpha = 0.7f)),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** The keyboard accessory row: Highlights opens the picker, and Done clears the review field's focus. */
@Composable
private fun AccessoryRow(accentText: Color, onOpenPicker: () -> Unit, onDone: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .background(AccessoryGround)
            .drawBehind {
                drawRect(
                    color = palette.ink0.copy(alpha = 0.12f),
                    topLeft = Offset.Zero,
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpenPicker)
                .padding(horizontal = 6.dp)
                .testTag(ReviewSheetTestTags.ACCESSORY_HIGHLIGHTS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = RibbonIcon,
                contentDescription = null,
                tint = accentText,
                modifier = Modifier.size(12.dp, 20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.book_detail_review_sheet_accessory_highlights),
                style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onDone)
                .padding(horizontal = 10.dp)
                .testTag(ReviewSheetTestTags.DONE),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.book_detail_review_sheet_accessory_done),
                style = uiTextStyle(16.sp, FontWeight.SemiBold, accentText),
            )
        }
    }
}

/** A dashed rounded outline drawn inside the modifier's bounds, as the private notes box draws it. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val half = 0.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - 2 * half, size.height - 2 * half),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        ),
    )
}

/** A ribbon, filled in the current tint: the From highlights and Highlights controls. */
private val RibbonIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewSheetRibbon",
        defaultWidth = 12.dp,
        defaultHeight = 20.dp,
        viewportWidth = 22f,
        viewportHeight = 40f,
    )
        .addPath(
            pathData = addPathNodes("M0 0H22V40L11 32L0 40Z"),
            fill = SolidColor(Color.White),
        )
        .build()
}

/** A padlock, for the private notes. */
private val LockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewSheetLock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M6 11h12v9H6zM8.5 11V7.5a3.5 3.5 0 0 1 7 0V11"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/** A plus, for opening the private notes. */
private val PlusIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewSheetPlus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M12 5v14M5 12h14"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        )
        .build()
}

/** An information mark, for the conflict note. */
private val InfoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewSheetInfo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 11v6M12 7.5v.5"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}
