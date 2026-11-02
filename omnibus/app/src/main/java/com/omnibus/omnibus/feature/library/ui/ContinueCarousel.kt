package com.omnibus.omnibus.feature.library.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.bookdetail.transition.BookCoverOrigin
import com.omnibus.omnibus.feature.bookdetail.transition.bookCoverSourceModifier
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.feature.library.formatDuration
import com.omnibus.omnibus.feature.library.settledCardId
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the Continue carousel, so UI tests can find its parts. */
internal object ContinueTestTags {
    const val CAROUSEL = "continue-carousel"
    const val DOTS = "continue-dots"
    fun dot(index: Int) = "continue-dot-$index"
    fun card(id: String) = "continue-card-$id"
    fun cover(id: String) = "continue-cover-$id"
    fun title(id: String) = "continue-title-$id"
    fun author(id: String) = "continue-author-$id"
    fun pill(id: String) = "continue-pill-$id"
    fun barTrack(id: String) = "continue-bar-track-$id"
    fun barFill(id: String) = "continue-bar-fill-$id"
}

/** Space the pager reserves above and below each card, so the card's shadow is not clipped. */
private val SHADOW_TOP = 8.dp
private val SHADOW_BOTTOM = 24.dp

/**
 * The pager state and the set of cards whose progress bar has drawn in. Both are hoisted above the
 * Library grid, so scrolling the carousel off screen and back keeps the card and replays nothing.
 */
@Stable
class ContinueCarouselState(
    val pagerState: PagerState,
    val drawnIn: SnapshotStateList<String>,
)

/** Remembers a [ContinueCarouselState] that survives the carousel leaving composition, and rotation. */
@Composable
fun rememberContinueCarouselState(pageCount: () -> Int): ContinueCarouselState {
    val pagerState = rememberPagerState(pageCount = pageCount)
    val drawnIn = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf() }
    return remember(pagerState, drawnIn) { ContinueCarouselState(pagerState, drawnIn) }
}

/**
 * The Continue carousel: one card per page, with page dots once there are two or more cards.
 *
 * The pager reports its settled page as a card id through [onSettled], and follows [selectedId]
 * when the selection changes from outside, for example after a refresh.
 *
 * @param onSettled Called with the id of the card the pager has settled on, when it differs from [selectedId].
 * @param onOpenBook Called with the book's uuid when the cover or title is tapped.
 * @param onResume Called with the card id when the resume pill, or any other part of the card, is tapped.
 * @param onShowContextMenu Called with the book on a long press of the card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContinueCarousel(
    cards: List<ContinueCard>,
    selectedId: String?,
    state: ContinueCarouselState,
    onSettled: (cardId: String) -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    onResume: (cardId: String) -> Unit,
    onShowContextMenu: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = state.pagerState
    val currentCards by rememberUpdatedState(cards)
    val currentSelected by rememberUpdatedState(selectedId)
    val currentOnSettled by rememberUpdatedState(onSettled)
    // The pager can ask for a key before this composition has seen a list that grew or shrank, so
    // read the latest cards and fall back to the index rather than indexing a stale list.
    val currentKeys by rememberUpdatedState(cards)

    // Keyed on the pager only: restarting on a new list would re-send a settled index that
    // still belongs to the old list. The first value is the page the pager starts on, not a swipe,
    // so it is dropped; the selection sync below moves the pager to the selected card.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { index ->
            val id = settledCardId(index, currentCards.map { it.id }) ?: return@collect
            if (id != currentSelected) currentOnSettled(id)
        }
    }
    // Follows the view model's selection, for example a refresh that dropped the visible card.
    LaunchedEffect(selectedId, cards) {
        val target = cards.indexOfFirst { it.id == selectedId }
        if (target >= 0 && target != pagerState.settledPage) pagerState.scrollToPage(target)
    }

    Column(modifier.fillMaxWidth().testTag(ContinueTestTags.CAROUSEL)) {
        HorizontalPager(
            state = pagerState,
            key = { index -> currentKeys.getOrNull(index)?.id ?: index },
            modifier = Modifier.fillMaxWidth().bleedVertically(top = SHADOW_TOP, bottom = SHADOW_BOTTOM),
        ) { page ->
            ContinueCardView(
                card = cards[page],
                state = state,
                onOpenBook = onOpenBook,
                onResume = onResume,
                onShowContextMenu = onShowContextMenu,
                modifier = Modifier.padding(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = SHADOW_TOP,
                    bottom = SHADOW_BOTTOM,
                ),
            )
        }
        if (cards.size >= 2) {
            Spacer(Modifier.height(10.dp))
            PageDots(
                count = cards.size,
                selected = pagerState.settledPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * Lets the pager draw [top] above and [bottom] below the slot it takes, so the card shadow isn't
 * clipped. The gaps around the carousel are measured from the card, not the page.
 */
private fun Modifier.bleedVertically(top: Dp, bottom: Dp) = layout { measurable, constraints ->
    val topPx = top.roundToPx()
    val extra = topPx + bottom.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = extra))
    layout(placeable.width, placeable.height - extra) { placeable.place(0, -topPx) }
}

/** One dot per card. The selected dot is a wider, brighter capsule, and both animate between pages. */
@Composable
private fun PageDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Row(
        modifier = modifier
            .testTag(ContinueTestTags.DOTS)
            .semantics { hideFromAccessibility() },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val isSelected = index == selected
            val width by animateDpAsState(
                targetValue = if (isSelected) 16.dp else 5.dp,
                animationSpec = Motion.snap(Dp.VisibilityThreshold),
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) palette.ink1 else palette.ink3.copy(alpha = 0.4f),
                animationSpec = Motion.snap<Color>(),
            )
            Box(
                Modifier
                    .testTag(ContinueTestTags.dot(index))
                    .semantics { hideFromAccessibility() }
                    .size(width = width, height = 5.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * One Continue card: the cover, the eyebrow, title and author, then the progress bar or "Last
 * opened", and the position label with the resume pill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCardView(
    card: ContinueCard,
    state: ContinueCarouselState,
    onOpenBook: (uuid: String) -> Unit,
    onResume: (cardId: String) -> Unit,
    onShowContextMenu: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val book = card.book
    val identity = remember(book) { book.coverIdentity() }
    val colors = remember(identity.tone, palette.bg0) {
        continueCardColors(identity.tone, isLightGround(palette.bg0))
    }
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(Radius.lg)
    val listening = card.medium == ProgressFormat.Listening
    val eyebrowRes = if (listening) R.string.continue_eyebrow_listening else R.string.continue_eyebrow_reading
    val resumeLabel = stringResource(if (listening) R.string.continue_play else R.string.continue_read)
    val openDetails = stringResource(R.string.continue_open_details)

    val openBook = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onOpenBook(book.uuid)
    }
    val resume = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onResume(card.id)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .testTag(ContinueTestTags.card(card.id))
            .cardShadow(Radius.lg)
            .clip(shape)
            .continueGround(colors)
            .border(
                0.5.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))),
                shape,
            )
            .combinedClickable(
                onClickLabel = resumeLabel,
                onLongClick = { onShowContextMenu(book) },
                onClick = resume,
            )
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                bookCoverSourceModifier(book.uuid, BookCoverOrigin.Continue)
                    .width(96.dp)
                    .testTag(ContinueTestTags.cover(card.id))
                    .clickable(onClickLabel = openDetails, onClick = openBook)
                    .semantics { contentDescription = book.displayTitle },
            ) {
                BookCover(
                    identity = identity,
                    modifier = Modifier.fillMaxWidth().coverShadow(6.dp, strength = 1.2f),
                    size = CoverSize.Md,
                    cornerRadius = Radius.sm,
                )
            }
            ContinueCardTextColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                eyebrow = {
                    Text(
                        text = stringResource(eyebrowRes).uppercase(),
                        style = uiTextStyle(10.5.sp, FontWeight.SemiBold, colors.rule)
                            .copy(letterSpacing = 0.6.sp),
                        maxLines = 1,
                    )
                },
                title = {
                    Text(
                        text = book.displayTitle,
                        style = displayTextStyle(24, palette.ink0),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .testTag(ContinueTestTags.title(card.id))
                            .clickable(onClickLabel = openDetails, onClick = openBook),
                    )
                },
                author = {
                    Text(
                        text = book.authorDisplay,
                        style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(ContinueTestTags.author(card.id)),
                    )
                },
                bottom = {
                    if (card.fraction != null) {
                        ContinueProgressBar(
                            card = card,
                            rule = colors.rule,
                            state = state,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.continue_last_opened, relativeAgeText(card.lastOpened)),
                            style = uiTextStyle(11.5.sp, FontWeight.Normal, palette.ink3),
                            maxLines = 1,
                        )
                    }
                },
                footer = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = positionLabelText(card.position),
                            style = uiTextStyle(11.5.sp, FontWeight.Medium, palette.ink2),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        ResumePill(
                            listening = listening,
                            label = resumeLabel,
                            onClick = resume,
                            modifier = Modifier.testTag(ContinueTestTags.pill(card.id)),
                        )
                    }
                },
            )
        }
    }
}

/** The "Read" or "Play" pill: an accent capsule with a book or play glyph. */
@Composable
private fun ResumePill(
    listening: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(palette.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (listening) R.drawable.ic_play else R.drawable.ic_book),
            contentDescription = null,
            tint = palette.accentInk,
            modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = uiTextStyle(12, FontWeight.SemiBold, palette.accentInk),
        )
    }
}

/**
 * The progress bar, a 3 dp capsule with a lit end cap and a glow. It draws in once per card id, and
 * never again after the card has been disposed and recreated.
 */
@Composable
private fun ContinueProgressBar(
    card: ContinueCard,
    rule: Color,
    state: ContinueCarouselState,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val animate = !reduced && card.id !in state.drawnIn
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            // Marked at the start, so a card disposed mid-draw never replays.
            state.drawnIn += card.id
            // Motion.page() (400 ms, 0.2/0.6/0.3/1) with the ticket's 120 ms delay.
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 400,
                    delayMillis = 120,
                    easing = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f),
                ),
            )
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(3.dp)) {
        val minWidth = 3.dp
        val target = maxOf(minWidth, maxWidth * (card.fraction ?: 0.0).toFloat())
        val width = minWidth + (target - minWidth) * progress.value
        Box(
            Modifier
                .matchParentSize()
                .testTag(ContinueTestTags.barTrack(card.id))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
        )
        Box(
            Modifier
                .width(width)
                .fillMaxHeight()
                .testTag(ContinueTestTags.barFill(card.id))
                .barGlow(rule.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(rule.copy(alpha = 0.75f), rule))),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(3.dp)
                    .fillMaxHeight()
                    .blur(1.dp)
                    .background(Color.White.copy(alpha = 0.55f)),
            )
        }
    }
}

/**
 * The text side of a card, as a custom layout so the title keeps its lines first. When the column is
 * short, the footer yields first, then the bottom block, then the eyebrow, then the author. A row
 * that doesn't fit is not placed.
 */
@Composable
private fun ContinueCardTextColumn(
    modifier: Modifier,
    eyebrow: @Composable () -> Unit,
    title: @Composable () -> Unit,
    author: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        contents = listOf(eyebrow, title, author, bottom, footer),
        modifier = modifier,
    ) { (eyebrowMeasurables, titleMeasurables, authorMeasurables, bottomMeasurables, footerMeasurables), constraints ->
        val gap = 5.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val height = constraints.maxHeight

        val titlePlaceable = titleMeasurables.first().measure(loose)
        var remaining = height - titlePlaceable.height

        val footerPlaceable = footerMeasurables.firstOrNull()
            ?.measure(loose.copy(minWidth = constraints.maxWidth))
            ?.takeIf { it.height <= remaining }
            ?.also { remaining -= it.height }
        val bottomPlaceable = bottomMeasurables.firstOrNull()
            ?.measure(loose)
            ?.takeIf { it.height <= remaining - gap }
            ?.also { remaining -= it.height + gap }
        val eyebrowPlaceable = eyebrowMeasurables.firstOrNull()
            ?.measure(loose)
            ?.takeIf { it.height <= remaining - gap }
            ?.also { remaining -= it.height + gap }
        val authorPlaceable = authorMeasurables.firstOrNull()
            ?.measure(loose)
            ?.takeIf { it.height <= remaining - gap }
            ?.also { remaining -= it.height + gap }

        layout(constraints.maxWidth, height) {
            var y = 0
            eyebrowPlaceable?.let {
                it.place(0, y)
                y += it.height + gap
            }
            titlePlaceable.place(0, y)
            y += titlePlaceable.height
            authorPlaceable?.let {
                y += gap
                it.place(0, y)
            }
            var bottomY = height
            footerPlaceable?.let {
                bottomY -= it.height
                it.place(0, bottomY)
            }
            bottomPlaceable?.let {
                bottomY -= it.height + gap
                it.place(0, bottomY)
            }
        }
    }
}

/** The position label, as its visible text. The string says what the label means, not its data. */
@Composable
private fun positionLabelText(label: PositionLabel): String = when (label) {
    is PositionLabel.Chapter -> stringResource(R.string.continue_chapter_of, label.number, label.total)
    is PositionLabel.Part -> stringResource(R.string.continue_part_of, label.number, label.total)
    is PositionLabel.TimeLeft -> stringResource(R.string.continue_time_left, formatDuration(label.seconds))
    is PositionLabel.PercentRead -> stringResource(R.string.continue_percent_read, label.percent)
    PositionLabel.InProgress -> stringResource(R.string.continue_in_progress)
}

/** The relative age, such as "5 min. ago", or "just now". */
@Composable
private fun relativeAgeText(age: RelativeAge): String = when (age) {
    RelativeAge.JustNow -> stringResource(R.string.continue_just_now)
    is RelativeAge.Seconds -> pluralStringResource(R.plurals.continue_seconds_ago, age.count, age.count)
    is RelativeAge.Minutes -> pluralStringResource(R.plurals.continue_minutes_ago, age.count, age.count)
    is RelativeAge.Hours -> pluralStringResource(R.plurals.continue_hours_ago, age.count, age.count)
    is RelativeAge.Days -> pluralStringResource(R.plurals.continue_days_ago, age.count, age.count)
    is RelativeAge.Weeks -> pluralStringResource(R.plurals.continue_weeks_ago, age.count, age.count)
    is RelativeAge.Months -> pluralStringResource(R.plurals.continue_months_ago, age.count, age.count)
    is RelativeAge.Years -> pluralStringResource(R.plurals.continue_years_ago, age.count, age.count)
}

private val CARD_HEIGHT = 176.dp

/** The card's base gradient and bloom, drawn behind its content and inside its clip. */
private fun Modifier.continueGround(colors: ContinueCardColors): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawBehind {
        drawRect(
            Brush.linearGradient(
                listOf(colors.baseStart, colors.baseEnd),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
        )
        // The bloom box's leading edge sits 60 dp left of the card's, centred vertically; the gradient is centred in it.
        val radius = 160.dp.toPx()
        val centreX = (-60).dp.toPx() + radius
        val center = Offset(
            if (layoutDirection == LayoutDirection.Rtl) size.width - centreX else centreX,
            size.height / 2,
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(colors.bloom, colors.bloom.copy(alpha = 0f)), center, radius),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus,
        )
    }

/** The card's drop shadow: black at 35%, about 18 dp blur, 8 dp down. Chain it before [clip]. */
private fun Modifier.cardShadow(cornerRadius: Dp): Modifier {
    val color = Color.Black.copy(alpha = 0.35f)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    asFrameworkPaint().setShadowLayer(18.dp.toPx(), 0f, 8.dp.toPx(), color.toArgb())
                }
                val radius = cornerRadius.toPx()
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
            }
        }
    } else {
        // Before API 28 a blurred shadow layer only draws under text, so approximate with an elevation.
        shadow(
            elevation = 18.dp,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = Color.Transparent,
            spotColor = color,
        )
    }
}

/** A soft glow of the bar's colour, drawn behind the fill and before its clip. */
private fun Modifier.barGlow(color: Color): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    this.color = color
                    asFrameworkPaint().setShadowLayer(4.dp.toPx(), 0f, 0f, color.toArgb())
                }
                val radius = size.height / 2
                canvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, paint)
            }
        }
    } else {
        this
    }

private fun previewBook(id: Long, title: String, author: String, formats: List<String>, accent: String) = Book(
    id = id,
    uniqueIdentifier = "preview-$id",
    filename = "$id.epub",
    title = title,
    creators = listOf(Creator(name = author, role = null, fileAs = null, id = null)),
    coverUrl = null,
    accent = accent,
    formats = formats,
    series = null,
    seriesIndex = null,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
)

@PreviewLightDark
@Composable
private fun ContinueCardChapterPreview() {
    OmnibusTheme {
        ContinueCardView(
            card = ContinueCard(
                id = "p1:audio",
                book = previewBook(1, "Project Hail Mary", "Andy Weir", listOf("m4b"), "oklch(0.72 0.14 55)"),
                medium = ProgressFormat.Listening,
                fraction = 0.4,
                position = PositionLabel.Chapter(14, 31),
                lastOpened = RelativeAge.Hours(3),
                resumeFileId = 7L,
            ),
            state = rememberPreviewCarouselState(1),
            onOpenBook = {},
            onResume = {},
            onShowContextMenu = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ContinueCardReadingPreview() {
    OmnibusTheme {
        ContinueCardView(
            card = ContinueCard(
                id = "p2:epub",
                book = previewBook(2, "Annihilation", "Jeff VanderMeer", listOf("epub"), "oklch(0.62 0.10 150)"),
                medium = ProgressFormat.Reading,
                fraction = 0.46,
                position = PositionLabel.PercentRead(46),
                lastOpened = RelativeAge.Minutes(5),
                resumeFileId = null,
            ),
            state = rememberPreviewCarouselState(1),
            onOpenBook = {},
            onResume = {},
            onShowContextMenu = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ContinueCardNoBarPreview() {
    OmnibusTheme {
        ContinueCardView(
            card = ContinueCard(
                id = "p3:epub",
                book = previewBook(3, "The Dispossessed", "Ursula K. Le Guin", listOf("epub"), "oklch(0.60 0.09 200)"),
                medium = ProgressFormat.Reading,
                fraction = null,
                position = PositionLabel.InProgress,
                lastOpened = RelativeAge.Days(3),
                resumeFileId = null,
            ),
            state = rememberPreviewCarouselState(1),
            onOpenBook = {},
            onResume = {},
            onShowContextMenu = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ContinueCarouselPreview() {
    val cards = listOf(
        ContinueCard(
            id = "p1:audio",
            book = previewBook(1, "Project Hail Mary", "Andy Weir", listOf("m4b"), "oklch(0.72 0.14 55)"),
            medium = ProgressFormat.Listening,
            fraction = 0.4,
            position = PositionLabel.Chapter(14, 31),
            lastOpened = RelativeAge.Hours(3),
            resumeFileId = 7L,
        ),
        ContinueCard(
            id = "p2:epub",
            book = previewBook(2, "Annihilation", "Jeff VanderMeer", listOf("epub"), "oklch(0.62 0.10 150)"),
            medium = ProgressFormat.Reading,
            fraction = 0.46,
            position = PositionLabel.PercentRead(46),
            lastOpened = RelativeAge.Minutes(5),
            resumeFileId = null,
        ),
        ContinueCard(
            id = "p3:epub",
            book = previewBook(3, "The Dispossessed", "Ursula K. Le Guin", listOf("epub"), "oklch(0.60 0.09 200)"),
            medium = ProgressFormat.Reading,
            fraction = null,
            position = PositionLabel.InProgress,
            lastOpened = RelativeAge.Days(3),
            resumeFileId = null,
        ),
    )
    OmnibusTheme {
        val state = rememberContinueCarouselState(pageCount = { cards.size })
        ContinueCarousel(
            cards = cards,
            selectedId = cards.first().id,
            state = state,
            onSettled = {},
            onOpenBook = {},
            onResume = {},
            onShowContextMenu = {},
        )
    }
}

@Composable
private fun rememberPreviewCarouselState(count: Int): ContinueCarouselState =
    rememberContinueCarouselState(pageCount = { count })
