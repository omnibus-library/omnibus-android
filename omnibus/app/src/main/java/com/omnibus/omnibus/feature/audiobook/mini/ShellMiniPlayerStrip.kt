package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The docked mini player on the tab bar (#286 R1-R3, R12, R18). Expanded it shows the full row with its progress line
 * on the top edge; collapsed it shows one line with the progress line on the bottom edge. The fill runs edge to edge
 * over the tab bar, whose top hairline is the seam. [reduceMotion] switches the two states in one step, with no
 * transition.
 */
@Composable
internal fun ShellMiniPlayerStrip(
    uiState: MiniPlayerUIState,
    collapsed: Boolean,
    reduceMotion: Boolean,
    actions: MiniPlayerActions,
    modifier: Modifier = Modifier,
) {
    val percentLabel = uiState.percentListened?.let { stringResource(R.string.audiobook_mini_percent_listened, it) }
    Column(modifier.fillMaxWidth().background(OmnibusTheme.palette.bg2).testTag(MiniPlayerTestTags.BAR)) {
        if (reduceMotion) {
            StripState(uiState, collapsed, actions, percentLabel)
        } else {
            // The height animates; the Scaffold's bottom bar reports it, so the content's bottom inset follows.
            AnimatedContent(
                targetState = collapsed,
                contentAlignment = Alignment.BottomStart,
                transitionSpec = {
                    (fadeIn(Motion.settle()) togetherWith fadeOut(Motion.settle()))
                        .using(SizeTransform(clip = true) { _, _ -> Motion.settle(IntSize.VisibilityThreshold) })
                },
                label = "docked mini player",
            ) { isCollapsed ->
                StripState(uiState, isCollapsed, actions, percentLabel)
            }
        }
    }
}

/** The expanded or collapsed layout for [collapsed]. */
@Composable
private fun StripState(
    uiState: MiniPlayerUIState,
    collapsed: Boolean,
    actions: MiniPlayerActions,
    percentLabel: String?,
) {
    if (collapsed) {
        CollapsedStrip(uiState, actions, percentLabel)
    } else {
        ExpandedStrip(uiState, actions, percentLabel)
    }
}

/** The full row: progress line on top, then the body and the back, play/pause and stop buttons. */
@Composable
private fun ExpandedStrip(uiState: MiniPlayerUIState, actions: MiniPlayerActions, percentLabel: String?) {
    Column(Modifier.fillMaxWidth().testTag(MiniPlayerTestTags.STRIP_EXPANDED)) {
        ProgressLine(uiState)
        MiniPlayerRow(
            uiState = uiState,
            onExpand = actions.onExpand,
            onSkipBack = actions.onSkipBack,
            onPlayPauseToggled = actions.onPlayPauseToggled,
            onStop = actions.onStop,
            coverWidth = 30.dp,
            coverRadius = 4.dp,
            coverGap = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(StripHorizontalInsets)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            progressDescription = percentLabel,
        )
    }
}

/** One line: a glyph and the title as the body, then play/pause and stop. The progress line sits on the bottom edge. */
@Composable
private fun CollapsedStrip(uiState: MiniPlayerUIState, actions: MiniPlayerActions, percentLabel: String?) {
    val palette = OmnibusTheme.palette
    val openLabel = stringResource(R.string.audiobook_mini_open_player)
    Column(Modifier.fillMaxWidth().testTag(MiniPlayerTestTags.STRIP_COLLAPSED)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(StripHorizontalInsets)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .testTag(MiniPlayerTestTags.BODY)
                    .clickable(onClickLabel = openLabel, role = Role.Button) { actions.onExpand(uiState.bookUuid) }
                    .semantics(mergeDescendants = true) { if (percentLabel != null) stateDescription = percentLabel },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_audiobook_mini_now_playing),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = palette.accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uiState.title,
                    style = uiTextStyle(13.5.sp, FontWeight.SemiBold, palette.ink0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiniButton(
                iconRes = if (uiState.isPlaying) R.drawable.ic_audiobook_player_pause else R.drawable.ic_play,
                label = stringResource(if (uiState.isPlaying) R.string.audiobook_pause else R.string.audiobook_play),
                tag = MiniPlayerTestTags.PLAY_PAUSE,
                onClick = actions.onPlayPauseToggled,
            )
            MiniButton(
                iconRes = R.drawable.ic_audiobook_mini_close,
                label = stringResource(R.string.audiobook_mini_stop),
                tag = MiniPlayerTestTags.STOP,
                onClick = actions.onStop,
            )
        }
        ProgressLine(uiState)
    }
}

/**
 * The 2dp line for the whole-book progress (#286 R12): an accent fill over a faint track, so a short fill reads as
 * progress rather than a stray mark. The fill follows the whole-number percentage, so it agrees with the label and the
 * rail's bar. Drawn only while the percentage is known.
 */
@Composable
private fun ProgressLine(uiState: MiniPlayerUIState) {
    val percent = uiState.percentListened
    if (percent != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(OmnibusTheme.palette.line2)
                .testTag(MiniPlayerTestTags.PROGRESS_TRACK),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent / 100f)
                    .background(OmnibusTheme.palette.accent)
                    .testTag(MiniPlayerTestTags.PROGRESS_FILL),
            )
        }
    }
}

/** The system bars and cutout on the strip's sides, so its rows clear them while its fill still runs edge to edge. */
private val StripHorizontalInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
