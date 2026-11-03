package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.components.coverShadow
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

/** The target panel's fill, the design's #1f1b17. */
private val PanelFill = Color(0xFF1F1B17)

/** The tonal fill of Back and Try again, rgba(247, 244, 239, .1). */
private val TonalFill = Color(0xFFF7F4EF).copy(alpha = 0.10f)

/** The opacity of a disabled control, as in the More menu. */
private const val DisabledAlpha = 0.42f

/** The mark standing for the source title in the confirm template. */
private const val SOURCE_MARK = "\u0001"

/** The mark standing for the target title in the confirm template. */
private const val TARGET_MARK = "\u0002"

/**
 * The callbacks of the Merge with… sheet. The sheet's window and its body take the same set, so a close from the body
 * can animate the window closed first.
 */
@Immutable
internal class MergeSheetActions(
    val onQueryChange: (String) -> Unit = {},
    val onRetrySearch: () -> Unit = {},
    val onChoose: (Book) -> Unit = {},
    val onBack: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The Merge with… bottom sheet (#120). It cannot be dismissed while a merge runs: swipe, scrim taps and back are blocked,
 * and the close button is disabled. Closing from the close button hides the sheet first and then reports the close.
 *
 * @param target The book on the page, which every merge keeps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MergeSheet(state: MergeUIState, target: Book, actions: MergeSheetActions) {
    val palette = OmnibusTheme.palette
    val merging by rememberUpdatedState(state.merging)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !merging },
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = actions.onClose, // the ViewModel ignores closeSheet() while merging
        sheetState = sheetState,
        sheetGesturesEnabled = !state.merging,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = SheetContainer,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = true, // blocked by the BackHandler below while merging
            shouldDismissOnClickOutside = !state.merging,
        ),
    ) {
        // Back is consumed here while merging. The sheet's window reads shouldDismissOnBackPress only when it is
        // created, and its own handler asks the sheet to hide, which is refused while merging and leaves it stuck.
        BackHandler(enabled = state.merging) {}
        val closeWithAnimation = remember(actions, sheetState, scope) {
            MergeSheetActions(
                onQueryChange = actions.onQueryChange,
                onRetrySearch = actions.onRetrySearch,
                onChoose = actions.onChoose,
                onBack = actions.onBack,
                onConfirm = actions.onConfirm,
                onClose = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) actions.onClose()
                    }
                },
            )
        }
        MergeSheetContent(state = state, target = target, actions = closeWithAnimation)
    }
}

/**
 * The sheet's body, without the sheet window, so tests and previews can drive it. The title stays fixed and the body
 * scrolls. The search step fills most of the window; the confirmation wraps its content.
 */
@Composable
internal fun MergeSheetContent(
    state: MergeUIState,
    target: Book,
    actions: MergeSheetActions,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val title = stringResource(R.string.book_detail_merge_menu_row)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (state.step is MergeStep.Search) Modifier.fillMaxHeight(0.92f) else Modifier)
            .testTag(MergeTestTags.SHEET)
            .semantics { paneTitle = title }
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = displayTextStyle(28.sp, palette.ink0, FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag(MergeTestTags.TITLE),
            )
            MergeCloseButton(enabled = !state.merging, onClick = actions.onClose)
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            when (val step = state.step) {
                MergeStep.Search -> SearchStep(state, target, actions)
                is MergeStep.Confirm -> ConfirmStep(step.candidate, state, target, actions)
            }
        }
    }
}

/** The search step: the intro, the target panel, the field, the note and the result rows. */
@Composable
private fun SearchStep(state: MergeUIState, target: Book, actions: MergeSheetActions) {
    val palette = OmnibusTheme.palette
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    val searchLabel = stringResource(R.string.book_detail_merge_search_label)
    val searchingText = stringResource(R.string.book_detail_merge_searching)
    val chooseLabel = stringResource(R.string.book_detail_merge_choose_action)
    val sameSeries = stringResource(R.string.book_detail_merge_same_series)
    val note = mergeNote(state.query, state.searching, state.candidates.size, state.isOnline, state.searchFailed)

    // The field takes focus and the keyboard as the sheet opens.
    LaunchedEffect(Unit) { fieldFocus.requestFocus() }

    Text(
        text = stringResource(R.string.book_detail_merge_intro),
        style = uiTextStyle(15, FontWeight.Normal, SheetMuted),
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp).testTag(MergeTestTags.INTRO),
    )
    TargetPanel(target)
    Text(
        text = searchLabel,
        style = uiTextStyle(15, FontWeight.SemiBold, palette.ink0),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).testTag(MergeTestTags.LABEL),
    )
    OmnibusTextField(
        value = state.query,
        onValueChange = actions.onQueryChange,
        placeholder = stringResource(R.string.book_detail_merge_search_hint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            actions.onRetrySearch()
        }),
        modifier = Modifier
            .focusRequester(fieldFocus)
            .semantics { contentDescription = searchLabel }
            .testTag(MergeTestTags.FIELD),
    )

    // The live region wraps only the text, so the Try again pill below it is not part of the announcement.
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(top = 8.dp)
            .testTag(MergeTestTags.NOTE)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        when (note) {
            MergeNote.Idle -> NoteText(stringResource(R.string.book_detail_merge_note_idle))
            MergeNote.Searching -> CircularProgressIndicator(
                color = palette.ink2,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp).testTag(MergeTestTags.SPINNER).semantics {
                    contentDescription = searchingText
                },
            )
            is MergeNote.Count -> NoteText(
                pluralStringResource(R.plurals.book_detail_merge_note_count, note.count, note.count),
            )
            is MergeNote.NoMatch -> NoteText(stringResource(R.string.book_detail_merge_note_none, note.query))
            MergeNote.Offline -> NoteText(stringResource(R.string.book_detail_more_needs_connection))
            MergeNote.Failed -> Text(
                text = stringResource(R.string.book_detail_merge_search_failed),
                style = uiTextStyle(15, FontWeight.Normal, palette.bad),
            )
        }
    }
    if (note == MergeNote.Failed) {
        TonalPill(
            label = stringResource(R.string.book_detail_merge_retry),
            onClick = actions.onRetrySearch,
            modifier = Modifier.padding(top = 8.dp).testTag(MergeTestTags.RETRY),
        )
    }

    if (note is MergeNote.Count || note == MergeNote.Searching) {
        state.candidates.forEach { book ->
            CandidateRow(
                book = book,
                target = target,
                sameSeriesLabel = sameSeries,
                chooseLabel = chooseLabel,
                onChoose = { actions.onChoose(book) },
            )
        }
    }
}

/** The confirmation step: the two covers, the body, any error, then Back and Merge. */
@Composable
private fun ConfirmStep(candidate: Book, state: MergeUIState, target: Book, actions: MergeSheetActions) {
    val palette = OmnibusTheme.palette
    val bodyFocus = remember { FocusRequester() }
    val merging = stringResource(R.string.book_detail_merge_merging)
    val confirm = stringResource(R.string.book_detail_merge_confirm)

    // The body takes focus on entering the step, so TalkBack reads it in full.
    LaunchedEffect(candidate.uuid) { bodyFocus.requestFocus() }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag(MergeTestTags.CONFIRM_COVERS)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConfirmCover(candidate, width = 92, height = 138, rotation = 5f)
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_merge_arrow),
            contentDescription = null,
            tint = SheetMuted,
            modifier = Modifier.size(28.dp),
        )
        ConfirmCover(target, width = 104, height = 156, rotation = -2.5f)
    }

    Text(
        text = confirmBody(candidate.displayTitle, target.displayTitle),
        style = uiTextStyle(17, FontWeight.Normal, palette.ink0),
        modifier = Modifier.focusRequester(bodyFocus).focusable().testTag(MergeTestTags.CONFIRM_BODY),
    )
    if (!state.isOnline) {
        Text(
            text = stringResource(R.string.book_detail_more_needs_connection),
            style = uiTextStyle(14, FontWeight.Normal, SheetMuted),
            modifier = Modifier.padding(top = 12.dp).testTag(MergeTestTags.OFFLINE_NOTE),
        )
    }
    state.mergeError?.let { failure ->
        Text(
            text = stringResource(
                if (failure == MergeFailure.Gone) R.string.book_detail_merge_failed_gone else R.string.book_detail_merge_failed,
            ),
            style = uiTextStyle(15, FontWeight.Normal, palette.bad),
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag(MergeTestTags.MERGE_ERROR)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionPill(
            label = stringResource(R.string.book_detail_merge_back),
            enabled = !state.merging,
            fill = TonalFill,
            textColor = palette.ink0,
            onClick = actions.onBack,
            modifier = Modifier.weight(1f).testTag(MergeTestTags.BACK),
        )
        ActionPill(
            label = if (state.merging) merging else confirm,
            enabled = !state.merging && state.isOnline,
            fill = mergeButtonFill(target),
            textColor = Color.White,
            onClick = actions.onConfirm,
            modifier = Modifier
                .weight(1f)
                .testTag(MergeTestTags.MERGE)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The target panel: this book's cover, "Everything folds into" its title, and "This entry stays". */
@Composable
private fun TargetPanel(target: Book) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelFill)
            .padding(12.dp)
            .testTag(MergeTestTags.PANEL)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BookCover(
            identity = target.coverIdentity(),
            modifier = Modifier.size(34.dp, 51.dp),
            size = CoverSize.Sm,
            cornerRadius = 3.dp,
            keepsAspect = false,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.book_detail_merge_folds_into),
                style = uiTextStyle(13, FontWeight.Normal, SheetMuted),
            )
            Text(
                text = target.displayTitle,
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            )
        }
        Text(
            text = stringResource(R.string.book_detail_merge_entry_stays),
            style = uiTextStyle(13, FontWeight.Normal, SheetMuted),
        )
    }
}

/** One result row: a cover, the title, "Author · year", and the tags line. The whole row is one button. */
@Composable
private fun CandidateRow(
    book: Book,
    target: Book,
    sameSeriesLabel: String,
    chooseLabel: String,
    onChoose: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val tags = candidateTags(book, target, sameSeriesLabel)
    Row(
        Modifier
            .testTag(MergeTestTags.candidate(book.uuid))
            .topRule()
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(role = Role.Button, onClickLabel = chooseLabel, onClick = onChoose)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BookCover(
            identity = book.coverIdentity(),
            modifier = Modifier.size(40.dp, 60.dp),
            size = CoverSize.Sm,
            cornerRadius = 3.dp,
            keepsAspect = false,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = book.displayTitle,
                style = displayTextStyle(19.sp, palette.ink0, FontWeight.SemiBold),
            )
            Text(
                text = candidateMeta(book),
                style = uiTextStyle(14, FontWeight.Normal, SheetMuted),
            )
            if (tags.isNotEmpty()) {
                Text(text = tags, style = uiTextStyle(13, FontWeight.Normal, palette.ink0))
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_merge_chevron),
            contentDescription = null,
            tint = SheetMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** One cover of the confirm step, rotated and shadowed like the shelf sheet's stack covers. */
@Composable
private fun ConfirmCover(book: Book, width: Int, height: Int, rotation: Float) {
    Box(Modifier.size(width.dp, height.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .coverShadow(4.dp)
                .graphicsLayer { rotationZ = rotation },
        ) {
            BookCover(
                identity = book.coverIdentity(),
                modifier = Modifier.fillMaxSize(),
                size = CoverSize.Md,
                cornerRadius = 4.dp,
                keepsAspect = false,
            )
        }
    }
}

/** The close button at the title's end. It is dimmed and disabled while a merge runs. */
@Composable
private fun MergeCloseButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .testTag(MergeTestTags.CLOSE)
            .size(44.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = stringResource(R.string.book_detail_merge_close),
            tint = OmnibusTheme.palette.ink0,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A pill button that fills its space. Disabled, it is drawn at [DisabledAlpha] and reports itself disabled. */
@Composable
private fun ActionPill(
    label: String,
    enabled: Boolean,
    fill: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = uiTextStyle(16, FontWeight.SemiBold, textColor))
    }
}

/** A tonal pill, for Try again. */
@Composable
private fun TonalPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(TonalFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = uiTextStyle(15, FontWeight.SemiBold, OmnibusTheme.palette.ink0))
    }
}

/** A line of the note. */
@Composable
private fun NoteText(text: String) {
    Text(text = text, style = uiTextStyle(14, FontWeight.Normal, SheetMuted))
}

/**
 * The confirm body with each title in bold. The template is formatted with marks first, so a title is never searched for
 * in the copy.
 */
@Composable
private fun confirmBody(source: String, target: String): AnnotatedString {
    val template = stringResource(R.string.book_detail_merge_confirm_body, SOURCE_MARK, TARGET_MARK)
    return remember(template, source, target) {
        buildAnnotatedString {
            var rest = template
            while (rest.isNotEmpty()) {
                val i = rest.indexOfAny(charArrayOf(SOURCE_MARK[0], TARGET_MARK[0]))
                if (i < 0) {
                    append(rest)
                    break
                }
                append(rest.substring(0, i))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(if (rest[i] == SOURCE_MARK[0]) source else target)
                }
                rest = rest.substring(i + 1)
            }
        }
    }
}

/** The fill of the Merge pill: the deep end of the book's cover tone, the design's oklch(.44) of its tone. */
private fun mergeButtonFill(book: Book): Color {
    val tone = book.coverIdentity().tone
    return Oklch(0.44, tone.c, tone.h).toColor()
}

/** A hairline across the top of a row. */
private fun Modifier.topRule(): Modifier = drawBehind {
    drawLine(RowRule, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}
