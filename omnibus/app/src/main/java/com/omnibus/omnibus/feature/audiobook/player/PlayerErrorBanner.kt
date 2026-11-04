package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The playback error banner (R16), styled like the reader's sync offer. Retry is offered for every error, since
 * `play()` retries a failed load as well as a stopped playback (C2). The message is a polite live region.
 *
 * @param onRetry Called when the user taps Retry.
 */
@Composable
internal fun PlayerErrorBanner(
    error: PlaybackError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.ERROR_BANNER)
            .clip(shape)
            .background(palette.bg1)
            .border(0.5.dp, palette.bad.copy(alpha = 0.4f), shape)
            .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alert_triangle),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = palette.bad,
        )
        Text(
            text = playbackErrorText(error),
            modifier = Modifier
                .weight(1f)
                .testTag(PlayerTestTags.ERROR_MESSAGE)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = uiTextStyle(12, FontWeight.Normal, palette.ink0),
        )
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag(PlayerTestTags.RETRY)
                .clickable(role = Role.Button, onClick = onRetry),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.audiobook_player_retry),
                style = uiTextStyle(13, FontWeight.SemiBold, palette.accent),
            )
        }
    }
}

/** The copy for [error]. A server error without a usable message gets the generic line. */
@Composable
internal fun playbackErrorText(error: PlaybackError): String = when (error) {
    PlaybackError.NoAudioParts -> stringResource(R.string.audiobook_player_error_no_audio_parts)
    PlaybackError.TimelineFailed -> stringResource(R.string.audiobook_player_error_timeline)
    PlaybackError.PartUnreadable -> stringResource(R.string.audiobook_player_error_part_unreadable)
    is PlaybackError.Server -> error.message?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.audiobook_player_error_server)
    PlaybackError.StoppedAfterRetries -> stringResource(R.string.audiobook_player_error_stopped)
}
