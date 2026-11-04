package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The chapter-scoped scrubber (R10, R11). Tap or drag seeks on release, to book time within the span. The thumb holds
 * the released value until playback catches up, so it doesn't jump back while the engine seeks. Disabled while the span
 * is empty.
 *
 * @param spanStartSeconds Where the span starts in book time.
 * @param spanLengthSeconds The span's length. Zero disables the scrubber.
 * @param spanPositionSeconds The position within the span.
 * @param accent The track's fill and thumb colour.
 * @param onSeek Called with the book time to seek to, on release.
 */
@Composable
internal fun PlayerScrubber(
    spanStartSeconds: Double,
    spanLengthSeconds: Double,
    spanPositionSeconds: Double,
    durationSeconds: Double,
    rate: Double,
    hasChapters: Boolean,
    accent: Color,
    onSeek: (bookSeconds: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var committed by remember { mutableStateOf<Double?>(null) }   // span seconds, held after release

    val len = spanLengthSeconds.coerceAtLeast(0.0)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentStart by rememberUpdatedState(spanStartSeconds)
    val currentLen by rememberUpdatedState(len)
    val shown = (dragFraction?.let { it * len } ?: committed ?: spanPositionSeconds).coerceIn(0.0, len)
    val thumbScale by animateFloatAsState(
        targetValue = if (dragFraction != null) 1.35f else 1f,
        animationSpec = Motion.snap(),
        label = "scrubberThumb",
    )
    val fraction = if (len > 0) (shown / len).toFloat() else 0f
    val positionLabel = stringResource(
        if (hasChapters) R.string.audiobook_player_position_chapter else R.string.audiobook_player_position_book,
    )
    val trackColor = palette.ink3.copy(alpha = 0.35f)

    LaunchedEffect(committed) {
        if (committed != null) {
            delay(1_000)
            committed = null
        }
    }
    LaunchedEffect(spanPositionSeconds) {
        committed?.let { if (abs(spanPositionSeconds - it) < 1.5) committed = null }
    }

    val times = scrubberTimes(shown, len, spanStartSeconds, durationSeconds, rate, hasChapters)
    val labelStyle = uiTextStyle(12, FontWeight.Normal, palette.ink2).copy(fontFeatureSettings = "tnum")

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .testTag(PlayerTestTags.SCRUBBER)
                .semantics {
                    contentDescription = positionLabel
                    stateDescription = PlaybackTimeFormat.clock(shown)
                    progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..len.toFloat())
                    if (len > 0) {
                        setProgress { value ->
                            currentOnSeek(spanStartSeconds + value.coerceIn(0f, len.toFloat()))
                            true
                        }
                    }
                }
                .pointerInput(len > 0) {
                    if (currentLen <= 0) return@pointerInput
                    val inset = 7.dp.toPx()                                   // the thumb travels between its radii
                    fun fractionAt(x: Float) =
                        ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        var pressed = fractionAt(down.position.x)
                        dragFraction = pressed
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {                        // released: seek once
                                    change.consume()
                                    committed = pressed * currentLen
                                    currentOnSeek(currentStart + pressed * currentLen)
                                    break
                                }
                                pressed = fractionAt(change.position.x)
                                change.consume()
                                dragFraction = pressed
                            }
                        } finally {
                            dragFraction = null
                        }
                    }
                }
                .drawBehind { drawScrubber(fraction, accent, trackColor, thumbScale) },
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = times.elapsed,
                modifier = Modifier.testTag(PlayerTestTags.LABEL_ELAPSED),
                style = labelStyle,
            )
            times.bookLeft?.let {
                Text(
                    text = stringResource(R.string.audiobook_time_left, it),
                    modifier = Modifier.testTag(PlayerTestTags.LABEL_BOOK_LEFT),
                    style = labelStyle,
                )
            }
            Text(
                text = stringResource(R.string.audiobook_time_left, times.spanLeft),
                modifier = Modifier.testTag(PlayerTestTags.LABEL_SPAN_LEFT),
                style = labelStyle,
            )
        }
    }
}

/** The 4 dp track, the filled part and the thumb, which sits between its own radii. */
private fun DrawScope.drawScrubber(fraction: Float, accent: Color, track: Color, thumbScale: Float) {
    val inset = 7.dp.toPx()
    val y = size.height / 2f
    val startX = inset
    val endX = size.width - inset
    val fillX = startX + (endX - startX) * fraction
    val stroke = 4.dp.toPx()
    drawLine(color = track, start = Offset(startX, y), end = Offset(endX, y), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(color = accent, start = Offset(startX, y), end = Offset(fillX, y), strokeWidth = stroke, cap = StrokeCap.Round)
    drawCircle(color = accent, radius = 7.dp.toPx() * thumbScale, center = Offset(fillX, y))
}
