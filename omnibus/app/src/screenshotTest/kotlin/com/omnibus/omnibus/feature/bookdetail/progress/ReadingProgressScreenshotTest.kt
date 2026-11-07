package com.omnibus.omnibus.feature.bookdetail.progress

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingProgressEbookBar() {
    ReadingProgressShot(
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

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingProgressAudioBar() {
    ReadingProgressShot(
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

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingProgressInProgressOfflineNote() {
    ReadingProgressShot(
        ReadingProgressUIState(display = ProgressDisplay.InProgress, showOfflineNote = true),
        tone = Oklch(0.55, 0.12, 40.0),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingProgressBarOfflineNote() {
    ReadingProgressShot(
        ReadingProgressUIState(
            display = ProgressDisplay.Bar(
                fraction = 0.62f,
                percent = 62,
                isAudio = false,
                segments = barSegments(listOf(.25f, .25f, .25f, .25f), 0.62f),
                flag = FlagLabel.Chapter(6),
                tail = LineTail.LeftAtPace(6780),
            ),
            showOfflineNote = true,
        ),
        tone = Oklch(0.55, 0.12, 40.0),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingProgressHiddenOfflineNote() {
    ReadingProgressShot(
        ReadingProgressUIState(display = ProgressDisplay.Hidden, showOfflineNote = true),
        tone = Oklch(0.55, 0.12, 40.0),
    )
}

@Composable
private fun ReadingProgressShot(state: ReadingProgressUIState, tone: Oklch) {
    Shot {
        ReadingProgressContent(state, bookAccent(tone, lightGround = false), Modifier.padding(16.dp))
    }
}
