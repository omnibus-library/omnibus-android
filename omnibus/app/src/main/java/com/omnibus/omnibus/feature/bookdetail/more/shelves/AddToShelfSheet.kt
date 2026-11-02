package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.ui.components.StateActionButton
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** The sheet's muted text colour (the design's #c8c4be). */
private val SheetMuted = Color(0xFFC8C4BE)

/** The sheet's container, the design's #15120f. */
private val SheetContainer = Color(0xFF15120F)

/** The hairline above each row, rgba(247, 244, 239, .1). */
private val RowRule = Color(0xFFF7F4EF).copy(alpha = 0.10f)

/** The ring of an off toggle, rgba(247, 244, 239, .34). */
private val ToggleRing = Color(0xFFF7F4EF).copy(alpha = 0.34f)

/** The pop-in's easing, the design's cubic-bezier(.16, 1, .3, 1). */
private val PopEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** How far this book's cover starts above its slot when a shelf is turned on. */
private val PopDrop: Dp = 22.dp

/** The Add to shelf bottom sheet (#116): the reader's hand-picked shelves with a toggle each, and the smart shelves
 * the book matches. Closing hides the sheet first and then reports the close.
 *
 * @param snackbarHostState Shows a failed change inside the sheet, because the page's snackbar sits under the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToShelfSheet(
    content: ShelfSheetContent,
    book: Book,
    onToggle: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    reducedMotion: Boolean,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = shape,
        containerColor = SheetContainer,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        AddToShelfSheetContent(
            content = content,
            book = book,
            onToggle = onToggle,
            onRetry = onRetry,
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onClose()
                }
            },
            snackbarHostState = snackbarHostState,
            reducedMotion = reducedMotion,
        )
    }
}

/**
 * The sheet's body, without the sheet window, so tests and previews can drive it. The title stays fixed; the body
 * scrolls. Rows report taps through [onToggle]; the caller owns the state.
 *
 * @param snackbarHostState When non-null, a host for failure messages is drawn at the sheet's bottom.
 * @param reducedMotion When true, this book's cover appears in its place without the pop-in.
 */
@Composable
internal fun AddToShelfSheetContent(
    content: ShelfSheetContent,
    book: Book,
    onToggle: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    Box(modifier.fillMaxWidth().fillMaxHeight(0.82f).testTag(ShelfSheetTestTags.SHEET)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.book_detail_more_add_to_shelf),
                    style = displayTextStyle(28.sp, palette.ink0, FontWeight.SemiBold),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                        .testTag(ShelfSheetTestTags.TITLE),
                )
                CloseButton(onClose)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp),
            ) {
                when (content) {
                    ShelfSheetContent.Loading -> LoadingBody()
                    ShelfSheetContent.Failed -> FailedBody(onRetry)
                    is ShelfSheetContent.Loaded -> LoadedBody(content, book, onToggle, reducedMotion)
                }
            }
        }
        if (snackbarHostState != null) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = palette.bg2,
                    contentColor = palette.ink0,
                )
            }
        }
    }
}

/** The 44 dp close button at the title's end. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier
            .testTag(ShelfSheetTestTags.CLOSE)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = stringResource(R.string.book_detail_more_shelf_close),
            tint = OmnibusTheme.palette.ink0,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The spinner shown while the shelves are read. */
@Composable
private fun LoadingBody() {
    val palette = OmnibusTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp).testTag(ShelfSheetTestTags.LOADING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = palette.ink2,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.book_detail_more_shelf_loading),
            style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
        )
    }
}

/** The read failure, with a retry. */
@Composable
private fun FailedBody(onRetry: () -> Unit) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp).testTag(ShelfSheetTestTags.ERROR),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.book_detail_more_shelf_error),
            style = uiTextStyle(15, FontWeight.Normal, palette.ink0),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        StateActionButton(label = stringResource(R.string.book_detail_more_shelf_retry), onClick = onRetry)
    }
}

/** The loaded sheet: the intro and manual rows (or the empty text), then the smart shelves the book matches. */
@Composable
private fun LoadedBody(
    content: ShelfSheetContent.Loaded,
    book: Book,
    onToggle: (Long) -> Unit,
    reducedMotion: Boolean,
) {
    val palette = OmnibusTheme.palette
    if (content.manual.isEmpty()) {
        Text(
            text = stringResource(R.string.book_detail_more_shelf_empty),
            style = uiTextStyle(15, FontWeight.Normal, palette.ink0),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).testTag(ShelfSheetTestTags.EMPTY),
        )
    } else {
        Text(
            text = stringResource(R.string.book_detail_more_shelf_intro),
            style = uiTextStyle(14, FontWeight.Normal, SheetMuted),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).testTag(ShelfSheetTestTags.INTRO),
        )
        content.manual.forEach { row ->
            ManualShelfRowItem(row, book, onToggle, reducedMotion)
        }
    }
    if (content.smart.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.book_detail_more_shelf_smart_title),
            style = uiTextStyle(15, FontWeight.SemiBold, palette.ink0),
            modifier = Modifier.semantics { heading() }.testTag(ShelfSheetTestTags.SMART_TITLE),
        )
        Text(
            text = stringResource(R.string.book_detail_more_shelf_smart_note),
            style = uiTextStyle(14, FontWeight.Normal, SheetMuted),
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
        content.smart.forEach { row ->
            SmartShelfRowItem(row, reducedMotion)
        }
    }
}

/**
 * A hand-picked shelf: one checkbox for TalkBack, with the name, count and toggle. Its cover stack is decorative.
 */
@Composable
private fun ManualShelfRowItem(
    row: ManualShelfRow,
    book: Book,
    onToggle: (Long) -> Unit,
    reducedMotion: Boolean,
) {
    val palette = OmnibusTheme.palette
    val addLabel = stringResource(R.string.book_detail_more_shelf_add_action)
    val removeLabel = stringResource(R.string.book_detail_more_shelf_remove_action)
    val count = row.bookCount.toInt()
    val countText = pluralStringResource(R.plurals.book_detail_more_shelf_book_count, count, count)
    val onColor = remember(book) { shelfToggleOnColor(book) }
    Row(
        Modifier
            .testTag(ShelfSheetTestTags.row(row.id))
            .topRule()
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .toggleable(value = row.isOn, role = Role.Checkbox, onValueChange = { onToggle(row.id) })
            .semantics(mergeDescendants = true) {
                onClick(label = if (row.isOn) removeLabel else addLabel) {
                    onToggle(row.id)
                    true
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShelfCoverStack(
            rowId = row.id,
            coverUuids = row.otherCoverUuids,
            isOn = row.isOn,
            bookCover = book.coverIdentity(),
            reducedMotion = reducedMotion,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            )
            Text(
                text = countText,
                style = uiTextStyle(14, FontWeight.Normal, SheetMuted),
            )
        }
        ShelfToggle(
            isOn = row.isOn,
            onColor = onColor,
            modifier = Modifier.testTag(ShelfSheetTestTags.toggle(row.id)),
        )
    }
}

/** A smart shelf the book matches: read only, with its cover stack dimmed and its rule text when it has one. */
@Composable
private fun SmartShelfRowItem(row: SmartShelfRow, reducedMotion: Boolean) {
    val palette = OmnibusTheme.palette
    val resources = LocalContext.current.resources
    val words = rememberRuleWords()
    val count = row.bookCount.toInt()
    val countText = pluralStringResource(R.plurals.book_detail_more_shelf_book_count, count, count)
    val ruleText = row.rules?.let { smartShelfRuleText(it, words) }
    val meta = smartShelfMeta(countText, ruleText) { a, b ->
        resources.getString(R.string.book_detail_more_shelf_meta, a, b)
    }
    Row(
        Modifier
            .testTag(ShelfSheetTestTags.smartRow(row.id))
            .topRule()
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShelfCoverStack(
            rowId = row.id,
            coverUuids = row.coverUuids,
            isOn = false,
            bookCover = null,
            reducedMotion = reducedMotion,
            dimmed = true,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = uiTextStyle(16, FontWeight.Medium, palette.ink0),
            )
            Text(
                text = meta,
                style = uiTextStyle(14, FontWeight.Normal, SheetMuted),
            )
        }
    }
}

/**
 * A row's cover stack: up to three covers of other books, overlapping and rotated. When [bookCover] is given (a
 * hand-picked shelf), this book's cover sits on top at the end, and turning the shelf on pops it in.
 *
 * @param coverUuids The covers of other books, in shelf order.
 * @param isOn Whether the book is on the shelf; with the book's cover shown, two other covers make room for it.
 * @param dimmed Renders a read-only smart shelf's stack at 82 % opacity.
 */
@Composable
internal fun ShelfCoverStack(
    rowId: Long,
    coverUuids: List<String>,
    isOn: Boolean,
    bookCover: CoverIdentity?,
    reducedMotion: Boolean,
    dimmed: Boolean = false,
) {
    val others = coverUuids.take(if (bookCover != null && isOn) 2 else 3)
    Box(
        Modifier
            .testTag(ShelfSheetTestTags.stack(rowId))
            .width(66.dp)
            .height(52.dp)
            .alpha(if (dimmed) 0.82f else 1f)
            .clearAndSetSemantics {},
    ) {
        others.forEachIndexed { index, uuid ->
            StackCover(
                identity = CoverIdentity(uuid = uuid, title = "", hasCover = true),
                rotation = when {
                    dimmed -> (index - 1) * 3f
                    index == 0 -> -4f
                    else -> 2f
                },
                modifier = Modifier
                    .offset(x = (16 * index).dp)
                    .testTag(ShelfSheetTestTags.cover(rowId, index)),
            )
        }
        if (bookCover != null) {
            BookCoverPop(rowId, bookCover, isOn, slot = others.size, reducedMotion = reducedMotion)
        }
    }
}

/** This book's cover at its slot in a stack. It pops in from above and tilts to 4 degrees when its shelf turns on. */
@Composable
private fun BookCoverPop(
    rowId: Long,
    identity: CoverIdentity,
    isOn: Boolean,
    slot: Int,
    reducedMotion: Boolean,
) {
    val popDropPx = with(LocalDensity.current) { -PopDrop.roundToPx() }
    // Starts at the state the row had when first composed, so covers already on do not pop when the sheet opens.
    val visibleState = remember { MutableTransitionState(isOn) }.apply { targetState = isOn }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = Modifier.offset(x = (16 * slot).dp),
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(420, easing = PopEasing)) +
                slideInVertically(tween(420, easing = PopEasing)) { popDropPx }
        },
        exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(200)),
    ) {
        val animated by transition.animateFloat(
            transitionSpec = { tween(420, easing = PopEasing) },
            label = "pop-rotate",
        ) { if (it == EnterExitState.Visible) 4f else -14f }
        StackCover(
            identity = identity,
            rotation = if (reducedMotion) 4f else animated,
            modifier = Modifier.testTag(ShelfSheetTestTags.bookCover(rowId)),
        )
    }
}

/** One cover of a stack: a 30 x 45 dp book with a shadow, rotated by [rotation] degrees about its centre. */
@Composable
private fun StackCover(identity: CoverIdentity, rotation: Float, modifier: Modifier = Modifier) {
    Box(modifier.size(30.dp, 45.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .coverShadow(2.dp)
                .graphicsLayer { rotationZ = rotation },
        ) {
            BookCover(
                identity = identity,
                modifier = Modifier.fillMaxSize(),
                size = CoverSize.Sm,
                cornerRadius = 2.dp,
                keepsAspect = false,
            )
        }
    }
}

/** The round toggle: filled with the book's deep accent and a check when on, a ring when off. */
@Composable
private fun ShelfToggle(isOn: Boolean, onColor: Color, modifier: Modifier = Modifier) {
    val base = modifier.size(26.dp).clip(CircleShape)
    if (isOn) {
        Box(base.background(onColor), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
    } else {
        Box(base.border(1.5.dp, ToggleRing, CircleShape))
    }
}

/** The on fill of a book's toggles: the deep end of its cover accent, the design's oklch(.44) of the book's tone. */
internal fun shelfToggleOnColor(book: Book): Color {
    val tone = book.coverIdentity().tone
    return Oklch(0.44, tone.c, tone.h).toColor()
}

/** A hairline across the top of a row. */
private fun Modifier.topRule(): Modifier = drawBehind {
    drawLine(RowRule, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
}
