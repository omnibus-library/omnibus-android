package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing

/**
 * The player's top bar (R6). The chevron at the start collapses the player. At the end, each control shows only under
 * its rule: the sleep pill while a timer has time left, the ebook button for a book with an ebook, and the bookmarks
 * button.
 */
@Composable
internal fun PlayerTopBar(
    hasEbook: Boolean,
    sleepTimer: SleepTimerState?,
    actions: PlayerActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().testTag(PlayerTestTags.TOP_BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(
            iconRes = R.drawable.ic_audiobook_player_chevron_down,
            label = stringResource(R.string.audiobook_close_player),
            tag = PlayerTestTags.CLOSE,
            onClick = onClose,
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pill = actions.sleepPill
            if (pill != null && sleepTimer != null && sleepTimer.remainingSeconds > 0) {
                pill()
            }
            val openBook = actions.onOpenBook
            if (openBook != null && hasEbook) {
                RoundButton(
                    iconRes = R.drawable.ic_book,
                    label = stringResource(R.string.audiobook_player_open_book),
                    tag = PlayerTestTags.OPEN_BOOK,
                    onClick = openBook,
                )
            }
            val openBookmarks = actions.onOpenBookmarks
            if (openBookmarks != null) {
                RoundButton(
                    iconRes = R.drawable.ic_reader_bookmark,
                    label = stringResource(R.string.audiobook_player_bookmarks),
                    tag = PlayerTestTags.BOOKMARKS,
                    onClick = openBookmarks,
                )
            }
        }
    }
}

/** A round 38 dp button on `bg2` with an `ink1` glyph, inside a 48 dp touch target (iOS `circleButton`). */
@Composable
private fun RoundButton(iconRes: Int, label: String, tag: String, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .testTag(tag)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(palette.bg2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.ink1,
            )
        }
    }
}
