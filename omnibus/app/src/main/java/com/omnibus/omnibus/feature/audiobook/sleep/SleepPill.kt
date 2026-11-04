package com.omnibus.omnibus.feature.audiobook.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The top bar's moon pill: the time left on a running timer (P-SLP-4). It is one merged, non-interactive node whose
 * description is read on focus. It is never a live region, so the once-a-second change is not announced (P-A11Y-4).
 * The top bar decides when it shows.
 */
@Composable
fun SleepPill(state: SleepTimerState, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val clock = PlaybackTimeFormat.clock(state.remainingSeconds)
    val description = stringResource(R.string.audiobook_sleep_pill_description, clock)
    Row(
        modifier
            .testTag(SleepTestTags.PILL)
            .clip(CircleShape)
            .background(palette.bg2)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_audiobook_player_moon),
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(14.dp).testTag(SleepTestTags.PILL_MOON),
        )
        Text(
            text = clock,
            style = uiTextStyle(13, FontWeight.Medium, palette.accent),
        )
    }
}
