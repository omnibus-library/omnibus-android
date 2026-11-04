package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The transport row (R13): previous chapter, back 15, a large play/pause disc, forward 30 and next chapter. The chapter
 * steppers are disabled when there is nowhere to go. Every enabled press gives a light haptic tap (AND-UI-4).
 */
@Composable
internal fun PlayerTransport(
    isPlaying: Boolean,
    canPreviousChapter: Boolean,
    canNextChapter: Boolean,
    onPreviousChapter: () -> Unit,
    onSkipBack: () -> Unit,
    onPlayPauseToggled: () -> Unit,
    onSkipForward: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val playPauseLabel = stringResource(if (isPlaying) R.string.audiobook_pause else R.string.audiobook_play)
    fun press(action: () -> Unit): () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        action()
    }

    Row(
        modifier.fillMaxWidth().testTag(PlayerTestTags.TRANSPORT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stepper(
            iconRes = R.drawable.ic_audiobook_player_previous_chapter,
            iconSize = 26,
            label = stringResource(R.string.audiobook_previous_chapter),
            tag = PlayerTestTags.PREVIOUS,
            enabled = canPreviousChapter,
            tint = if (canPreviousChapter) palette.ink0 else palette.ink3,
            onClick = press(onPreviousChapter),
        )
        Stepper(
            iconRes = R.drawable.ic_audiobook_player_back_15,
            iconSize = 32,
            label = stringResource(R.string.audiobook_back_15),
            tag = PlayerTestTags.BACK_15,
            enabled = true,
            tint = palette.ink0,
            onClick = press(onSkipBack),
        )
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(palette.ink0)
                .testTag(PlayerTestTags.PLAY_PAUSE)
                .clickable(role = Role.Button, onClick = press(onPlayPauseToggled))
                .semantics { contentDescription = playPauseLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) R.drawable.ic_audiobook_player_pause else R.drawable.ic_play,
                ),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = palette.bg0,
            )
        }
        Stepper(
            iconRes = R.drawable.ic_audiobook_player_forward_30,
            iconSize = 32,
            label = stringResource(R.string.audiobook_forward_30),
            tag = PlayerTestTags.FORWARD_30,
            enabled = true,
            tint = palette.ink0,
            onClick = press(onSkipForward),
        )
        Stepper(
            iconRes = R.drawable.ic_audiobook_player_next_chapter,
            iconSize = 26,
            label = stringResource(R.string.audiobook_next_chapter),
            tag = PlayerTestTags.NEXT,
            enabled = canNextChapter,
            tint = if (canNextChapter) palette.ink0 else palette.ink3,
            onClick = press(onNextChapter),
        )
    }
}

/** One of the four side buttons. It takes the row's spare width equally, with a 48 dp minimum touch target. */
@Composable
private fun RowScope.Stepper(
    iconRes: Int,
    iconSize: Int,
    label: String,
    tag: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(iconSize.dp),
            tint = tint,
        )
    }
}
