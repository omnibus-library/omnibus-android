package com.omnibus.omnibus.feature.bookdetail.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The space between two segments of the bar. */
private val SegmentGap = 3.dp

/**
 * The reading progress block: the bar with its flag, the line under it, or "In progress", and the offline note. It draws
 * nothing when there is nothing to show, so the section takes no space.
 *
 * @param accent The book's accent, which fills the bar.
 */
@Composable
internal fun ReadingProgressContent(
    state: ReadingProgressUIState,
    accent: BookAccent,
    modifier: Modifier = Modifier,
) {
    if (state.display == ProgressDisplay.Hidden && !state.showOfflineNote) return
    val palette = OmnibusTheme.palette
    val locale: Locale = LocalConfiguration.current.locales[0]
    val strings = ProgressStrings(
        chapterFlag = stringResource(R.string.book_detail_progress_flag_chapter),
        percent = stringResource(R.string.book_detail_progress_percent),
        leftAtPace = stringResource(R.string.book_detail_progress_left_at_pace),
        leftOf = stringResource(R.string.book_detail_progress_left_of),
        underAMinute = stringResource(R.string.book_detail_progress_under_a_minute),
    )

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (val display = state.display) {
            ProgressDisplay.Hidden -> Unit
            ProgressDisplay.InProgress -> {
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.book_detail_progress_in_progress),
                    style = uiTextStyle(15, FontWeight.Normal, palette.ink1),
                    modifier = Modifier.testTag(ReadingProgressTestTags.IN_PROGRESS),
                )
            }
            is ProgressDisplay.Bar -> {
                val barLabel = stringResource(
                    if (display.isAudio) R.string.book_detail_progress_listening_cd else R.string.book_detail_progress_reading_cd,
                )
                val barState = String.format(
                    locale,
                    stringResource(R.string.book_detail_progress_percent_state),
                    display.percent,
                )
                Spacer(Modifier.height(8.dp))
                ProgressBarBlock(
                    bar = display,
                    flagText = display.flag.text(strings, locale),
                    accent = accent,
                    barLabel = barLabel,
                    barState = barState,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.ink0, fontWeight = FontWeight.SemiBold)) {
                            append(display.percentText(strings, locale))
                        }
                        display.tail?.let { append(it.text(strings, locale)) }
                    },
                    style = uiTextStyle(15, FontWeight.Normal, palette.ink1).copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().testTag(ReadingProgressTestTags.LINE),
                )
            }
        }
        if (state.showOfflineNote) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .testTag(ReadingProgressTestTags.OFFLINE_NOTE)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_wifi_off),
                    contentDescription = null,
                    tint = palette.ink1,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.book_detail_progress_offline),
                    style = uiTextStyle(14, FontWeight.Normal, palette.ink1),
                )
            }
        }
    }
}

/**
 * The bar and its flag. The flag stands above the bar with a stem to the fill's edge, and both stay inside the bar's
 * ends. The whole block is one progress indicator for TalkBack, so no child merges on its own.
 */
@Composable
private fun ProgressBarBlock(
    bar: ProgressDisplay.Bar,
    flagText: String,
    accent: BookAccent,
    barLabel: String,
    barState: String,
) {
    val palette = OmnibusTheme.palette
    Layout(
        content = {
            Box(
                Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.ink0)
                    .padding(horizontal = 9.dp)
                    .testTag(ReadingProgressTestTags.FLAG),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    flagText,
                    style = uiTextStyle(13, FontWeight.SemiBold, palette.bg0),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.background(palette.ink0).testTag(ReadingProgressTestTags.STEM))
            Row(
                Modifier.fillMaxWidth().height(6.dp).testTag(ReadingProgressTestTags.TRACK),
                horizontalArrangement = Arrangement.spacedBy(SegmentGap),
            ) {
                bar.segments.forEachIndexed { index, segment ->
                    Box(
                        Modifier
                            .weight(segment.weight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(palette.ink0.copy(alpha = 0.2f))
                            .testTag(ReadingProgressTestTags.segment(index)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(segment.fill).background(accent.tint))
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ReadingProgressTestTags.BAR)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(bar.fraction.coerceIn(0f, 1f), 0f..1f)
                contentDescription = barLabel
                stateDescription = barState
            },
    ) { measurables, constraints ->
        val barWidth = constraints.maxWidth
        val pillGap = 4.dp.roundToPx()
        val stemWidth = 2.dp.roundToPx()
        val track = measurables[2].measure(Constraints.fixedWidth(barWidth))
        val pill = measurables[0].measure(Constraints(maxWidth = barWidth))
        val stem = measurables[1].measure(Constraints.fixed(stemWidth, pillGap + 2.dp.roundToPx()))
        val edge = fillEdgeX(bar.segments, barWidth.toFloat(), SegmentGap.toPx())
        val trackTop = pill.height + pillGap
        layout(barWidth, trackTop + track.height) {
            track.placeRelative(0, trackTop)
            stem.placeRelative(stemX(edge, barWidth.toFloat(), stemWidth.toFloat()).roundToInt(), pill.height)
            pill.placeRelative((flagCenterX(edge, barWidth.toFloat(), pill.width.toFloat()) - pill.width / 2f).roundToInt(), 0)
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadingProgressEbookPreview() {
    ReadingProgressPreview(
        ReadingProgressUIState(
            display = ProgressDisplay.Bar(
                fraction = 0.62f,
                percent = 62,
                isAudio = false,
                segments = barSegments(
                    listOf(.12f, .2f, .08f, .15f, .1f, .1f, .09f, .06f, .07f, .04f, .05f, .04f),
                    0.62f,
                ),
                flag = FlagLabel.Chapter(6),
                tail = LineTail.LeftAtPace(6780),
            ),
        ),
        tone = Oklch(0.55, 0.12, 40.0),
    )
}

@PreviewLightDark
@Composable
private fun ReadingProgressAudioPreview() {
    ReadingProgressPreview(
        ReadingProgressUIState(
            display = ProgressDisplay.Bar(
                fraction = 0.52f,
                percent = 52,
                isAudio = true,
                segments = barSegments(listOf(.3f, .4f, .3f), 0.52f),
                flag = FlagLabel.Elapsed(9060),
                tail = LineTail.LeftOf(6540, 17340),
            ),
        ),
        tone = Oklch(0.6, 0.1, 250.0),
    )
}

@PreviewLightDark
@Composable
private fun ReadingProgressInProgressPreview() {
    ReadingProgressPreview(
        ReadingProgressUIState(display = ProgressDisplay.InProgress, showOfflineNote = true),
        tone = Oklch(0.55, 0.12, 40.0),
    )
}

@Composable
private fun ReadingProgressPreview(state: ReadingProgressUIState, tone: Oklch) {
    OmnibusTheme {
        ReadingProgressContent(state, bookAccent(tone, lightGround = false), Modifier.padding(16.dp))
    }
}
