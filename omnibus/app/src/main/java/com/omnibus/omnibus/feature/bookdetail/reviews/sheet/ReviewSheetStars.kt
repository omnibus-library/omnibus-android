package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.reviews.halfStarRound
import com.omnibus.omnibus.feature.bookdetail.reviews.starNumber
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.halfStepAt
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.toggledRating
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the star row is animating. Rest under reduced motion. [pop] is the 0-based star popping. */
data class StarsMotion(val lifted: Boolean = false, val pop: Int? = null, val ripple: Boolean = false) {
    companion object {
        /** No motion: nothing lifted, popping or rippling. */
        val Rest = StarsMotion()
    }
}

/** The star row's [StarsMotion], as semantics, so tests can read the animation without looking at pixels. */
internal val ReviewStarsMotionKey = SemanticsPropertyKey<StarsMotion>("ReviewStarsMotion")

/** The number in the white bubble above the finger, while the finger is down on the row. */
internal val ReviewStarsBubbleKey = SemanticsPropertyKey<String>("ReviewStarsBubble")

/** The easing of every star and readout motion, as the design's pop curve. */
private val PopEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** The lift while the finger is down on the large row. */
private const val LIFT_SCALE = 1.06f

/** The star outline's colour, as the stars draw it. */
private const val OUTLINE_ALPHA = 0.62f

/**
 * The large rating input of the review sheet: five cells of 54x60 dp with 46 dp stars, a bubble with the number above
 * the finger while it is down, a readout under the stars, and pops, ripples and a lift as the value changes. It takes
 * a rating in half stars. A tap sets the half star under it, and a tap on the current rating clears it. A drag past
 * touch slop changes the rating live. A vertical swipe that the parent scroll takes changes nothing.
 *
 * The row is one adjustable node that reads "4.5 of 5 stars, Loved it" and takes half-star steps. Under [reducedMotion]
 * the stars do not animate, but the bubble still shows.
 *
 * @param rating The saved rating, or null when unrated.
 * @param onRatingChange Called with the new rating, or null when it is cleared.
 * @param tint The fill colour of the stars.
 * @param reducedMotion Whether the system asks for no animation.
 */
@Composable
internal fun ReviewSheetStars(
    rating: Float?,
    onRatingChange: (Float?) -> Unit,
    tint: Color,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val locale = LocalConfiguration.current.locales[0]
    val animated = !reducedMotion
    val scrub = remember { mutableStateOf<Float?>(null) }

    // The readout fades and slides when the committed rating changes, but not on the first composition.
    val readoutAlpha = remember { Animatable(1f) }
    val readoutShift = remember { Animatable(0f) }
    val committed = remember { mutableStateOf(rating) }
    LaunchedEffect(rating, animated) {
        if (committed.value == rating) return@LaunchedEffect
        committed.value = rating
        if (!animated) return@LaunchedEffect
        readoutAlpha.snapTo(0.2f)
        readoutShift.snapTo(7f)
        coroutineScope {
            launch { readoutAlpha.animateTo(1f, tween(240, easing = PopEasing)) }
            launch { readoutShift.animateTo(0f, tween(240, easing = PopEasing)) }
        }
    }

    // The readout follows the finger while it is down, and the committed rating otherwise.
    val readoutValue = scrub.value ?: rating
    val word = ratingWord(readoutValue)
    val wordText = word?.let { ratingWordText(it) }
    val readoutNumber = readoutValue?.let { starNumber(it, locale) }
    val numberStyle = displayTextStyle(30.sp, palette.ink0, FontWeight.SemiBold).toSpanStyle()
    val wordStyle = uiTextStyle(15.sp, FontWeight.Normal, Color(0xFFDBD7D1)).toSpanStyle()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        StarBox(
            rating = rating,
            onRatingChange = onRatingChange,
            scrub = scrub,
            cellWidth = 54.dp,
            cellHeight = 60.dp,
            starSize = 46.dp,
            strokeUnits = 1.2f,
            tint = tint,
            animated = animated,
            showBubble = true,
            tag = ReviewSheetTestTags.STARS,
        )
        Spacer(Modifier.height(4.dp))
        if (readoutNumber != null && wordText != null) {
            Text(
                text = buildAnnotatedString {
                    withStyle(numberStyle) { append(readoutNumber) }
                    append(' ')
                    withStyle(wordStyle) { append(wordText) }
                },
                style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink0),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .graphicsLayer {
                        alpha = readoutAlpha.value
                        translationY = readoutShift.value.dp.toPx()
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(ReviewSheetTestTags.READOUT),
            )
        } else {
            Text(
                text = stringResource(R.string.book_detail_review_sheet_readout_prompt),
                style = uiTextStyle(15.sp, FontWeight.Normal, Color(0xFFC8C4BE)),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .graphicsLayer {
                        alpha = readoutAlpha.value
                        translationY = readoutShift.value.dp.toPx()
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(ReviewSheetTestTags.READOUT),
            )
        }
    }
}

/**
 * The compact rating input for the sheet's typing mode: 24 dp stars in cells of 30x48 dp, with the committed rating and
 * its word on the right, "4.5 · Loved it", or "Not rated yet". It has the same gestures and semantics as
 * [ReviewSheetStars] but no bubble and no animation.
 *
 * @param rating The saved rating, or null when unrated.
 * @param onRatingChange Called with the new rating, or null when it is cleared.
 * @param tint The fill colour of the stars.
 */
@Composable
internal fun ReviewSheetCompactStars(
    rating: Float?,
    onRatingChange: (Float?) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val scrub = remember { mutableStateOf<Float?>(null) }
    val word = ratingWord(rating)
    val wordText = word?.let { ratingWordText(it) }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StarBox(
            rating = rating,
            onRatingChange = onRatingChange,
            scrub = scrub,
            cellWidth = 30.dp,
            cellHeight = 48.dp,
            starSize = 24.dp,
            strokeUnits = 1.5f,
            tint = tint,
            animated = false,
            showBubble = false,
            tag = ReviewSheetTestTags.COMPACT_STARS,
        )
        if (rating != null && wordText != null) {
            Text(
                text = stringResource(
                    R.string.book_detail_review_sheet_readout_compact,
                    starNumber(rating, locale),
                    wordText,
                ),
                style = uiTextStyle(14.sp, FontWeight.Normal, Color(0xFFC8C4BE)),
                modifier = Modifier.testTag(ReviewSheetTestTags.COMPACT_READOUT),
            )
        } else {
            Text(
                text = stringResource(R.string.book_detail_review_sheet_readout_compact_unrated),
                style = uiTextStyle(14.sp, FontWeight.Normal, Color(0xFFC8C4BE)),
                modifier = Modifier.testTag(ReviewSheetTestTags.COMPACT_READOUT),
            )
        }
    }
}

/**
 * The five-cell star row shared by both inputs: one gesture and semantics modifier, the star drawing, the bubble, and
 * the pops, ripples and lift. [scrub] is the half star under a press in progress; the rating is only committed on a
 * tap or when a drag passes touch slop.
 */
@Composable
private fun StarBox(
    rating: Float?,
    onRatingChange: (Float?) -> Unit,
    scrub: MutableState<Float?>,
    cellWidth: Dp,
    cellHeight: Dp,
    starSize: Dp,
    strokeUnits: Float,
    tint: Color,
    animated: Boolean,
    showBubble: Boolean,
    tag: String,
) {
    val palette = OmnibusTheme.palette
    val outline = palette.ink0.copy(alpha = OUTLINE_ALPHA)
    val locale = LocalConfiguration.current.locales[0]
    val description = stringResource(R.string.book_detail_review_sheet_stars_description)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val shown = scrub.value ?: rating ?: 0f
    val shownWord = ratingWord(shown)
    val stateText = if (shown > 0f && shownWord != null) {
        stringResource(
            R.string.book_detail_review_sheet_stars_state,
            starNumber(shown, locale),
            ratingWordText(shownWord),
        )
    } else {
        stringResource(R.string.book_detail_review_sheet_stars_unrated)
    }

    // The gesture handlers outlive recompositions, so they read the latest values through these.
    val currentRating by rememberUpdatedState(rating)
    val currentOnChange by rememberUpdatedState(onRatingChange)

    val scales = remember { List(5) { Animatable(1f) } }
    val turns = remember { List(5) { Animatable(0f) } }
    val rises = remember { List(5) { Animatable(0f) } }
    var motion by remember { mutableStateOf(StarsMotion.Rest) }
    val lift by animateFloatAsState(
        if (animated && scrub.value != null) LIFT_SCALE else 1f,
        tween(180, easing = PopEasing),
        label = "review_sheet_lift",
    )
    val previous = remember { mutableFloatStateOf(shown) }

    LaunchedEffect(shown, animated) {
        if (shown == previous.floatValue) return@LaunchedEffect
        previous.floatValue = shown
        if (!animated || shown <= 0f) return@LaunchedEffect
        scales.forEach { it.snapTo(1f) }
        turns.forEach { it.snapTo(0f) }
        rises.forEach { it.snapTo(0f) }
        if (shown == 5f) {
            motion = motion.copy(ripple = true, pop = null)
            coroutineScope {
                for (i in 0 until 5) {
                    launch {
                        delay(55L * i)
                        launch {
                            rises[i].animateTo(-9f, tween(192, easing = PopEasing))
                            rises[i].animateTo(0f, tween(288, easing = PopEasing))
                        }
                        turns[i].animateTo(-6f, tween(192, easing = PopEasing))
                        turns[i].animateTo(0f, tween(288, easing = PopEasing))
                    }
                }
            }
        } else {
            val i = ceil(shown).toInt() - 1
            motion = motion.copy(pop = i, ripple = false)
            coroutineScope {
                launch {
                    scales[i].animateTo(1.22f, tween(128, easing = PopEasing))
                    scales[i].animateTo(1f, tween(192, easing = PopEasing))
                }
                turns[i].animateTo(-6f, tween(128, easing = PopEasing))
                turns[i].animateTo(0f, tween(192, easing = PopEasing))
            }
        }
        motion = motion.copy(pop = null, ripple = false)
    }

    val shownMotion = if (animated) motion.copy(lifted = scrub.value != null) else StarsMotion.Rest

    var boxWidth by remember { mutableFloatStateOf(0f) }
    var bubbleWidth by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .width(cellWidth * 5)
            .height(cellHeight)
            .onSizeChanged { boxWidth = it.width.toFloat() }
            .pointerInput(rtl) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    fun step(x: Float): Float = halfStepAt(if (rtl) width - x else x, width)
                    val before = currentRating
                    // The preview shows at once; nothing is committed until a tap or a drag past slop.
                    scrub.value = step(down.position.x)
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop == null) {
                        // Released without a horizontal drag (a tap), or a scrolling parent took the pointer.
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        scrub.value = null
                        if (up != null && up.changedToUp()) {
                            currentOnChange(toggledRating(before, step(down.position.x)))
                        }
                        return@awaitEachGesture
                    }
                    var last = step(slop.position.x)
                    scrub.value = last
                    if (last != before) currentOnChange(last)
                    horizontalDrag(slop.id) { change ->
                        change.consume()
                        val value = step(change.position.x)
                        if (value != last) {
                            last = value
                            scrub.value = value
                            currentOnChange(value)
                        }
                    }
                    scrub.value = null
                }
            }
            .clearAndSetSemantics {
                testTag = tag
                contentDescription = description
                stateDescription = stateText
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..5f, steps = 9)
                setProgress { target ->
                    val next = halfStarRound(target).takeIf { it > 0f }
                    if (next != currentRating) currentOnChange(next)
                    true
                }
                this[ReviewStarsMotionKey] = shownMotion
                val held = scrub.value
                if (showBubble && held != null) this[ReviewStarsBubbleKey] = starNumber(held, locale)
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            for (index in 0 until 5) {
                val slot = index + 1
                val fill = when {
                    shown >= slot -> 1f
                    shown >= slot - 0.5f -> 0.5f
                    else -> 0f
                }
                Box(Modifier.size(cellWidth, cellHeight), contentAlignment = Alignment.Center) {
                    SheetStar(
                        fill = fill,
                        tint = tint,
                        outline = outline,
                        strokeUnits = strokeUnits,
                        modifier = Modifier
                            .size(starSize)
                            .graphicsLayer {
                                scaleX = lift * scales[index].value
                                scaleY = scaleX
                                rotationZ = turns[index].value
                                translationY = rises[index].value.dp.toPx()
                            },
                    )
                }
            }
        }

        val held = scrub.value
        if (showBubble && held != null) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { bubbleWidth = it.width.toFloat() }
                    .offset {
                        val fraction = bubbleFraction(held, rtl)
                        IntOffset(
                            x = (boxWidth * fraction - bubbleWidth / 2f).roundToInt(),
                            y = -(26.dp.roundToPx() + 9.dp.roundToPx()),
                        )
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = starNumber(held, locale),
                        style = uiTextStyle(14.sp, FontWeight.SemiBold, Color(0xFF0E0E10)),
                    )
                }
                Box(Modifier.size(width = 2.dp, height = 9.dp).background(Color.White))
            }
        }
    }
}

/**
 * Where the bubble for [value] sits along the row, as a fraction of its width from the start edge: over the centre of
 * the half or whole star it names. Right-to-left layouts mirror it.
 */
private fun bubbleFraction(value: Float, rtl: Boolean): Float {
    val fraction = (ceil(value) - 0.5f - (if (value % 1f != 0f) 0.25f else 0f)) / 5f
    return if (rtl) 1f - fraction else fraction
}

/**
 * One star filled to [fill], from 0 (empty) to 1 (full), from its layout's start edge. The geometry is the same as
 * [com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRatingInput]'s star: keep the two in step.
 */
@Composable
private fun SheetStar(
    fill: Float,
    tint: Color,
    outline: Color,
    strokeUnits: Float,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val scale = size.minDimension / 24f
        val path = Path().apply {
            moveTo(STAR_POINTS[0] * scale, STAR_POINTS[1] * scale)
            for (i in 2 until STAR_POINTS.size step 2) {
                lineTo(STAR_POINTS[i] * scale, STAR_POINTS[i + 1] * scale)
            }
            close()
        }
        val stroke = Stroke(width = strokeUnits * scale, join = StrokeJoin.Round)
        drawPath(path, outline, style = stroke)
        if (fill > 0f) {
            val width = size.width * fill
            val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
            clipRect(left = left, right = left + width) {
                drawPath(path, tint)
                drawPath(path, tint, style = stroke)
            }
        }
    }
}

/**
 * The star's outline in a 24-unit square: five points, in order. Copied from
 * [com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRatingInput], keep them in step.
 */
private val STAR_POINTS = floatArrayOf(
    12f, 2.8f, 14.8f, 8.6f, 21.1f, 9.5f, 16.5f, 13.9f, 17.6f, 20.2f,
    12f, 17.2f, 6.4f, 20.2f, 7.5f, 13.9f, 2.9f, 9.5f, 9.2f, 8.6f,
)
