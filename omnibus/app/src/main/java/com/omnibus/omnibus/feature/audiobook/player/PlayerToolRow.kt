package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor

/**
 * The tool row (R14): Speed, Car Mode, Sleep, Bookmark and Sync here, in that order. A cell shows only when its action is
 * set; Sync here also needs an ebook. The row keeps its height when empty, so cells arriving in later tickets don't move
 * the layout.
 */
@Composable
internal fun PlayerToolRow(
    rate: Double,
    hasEbook: Boolean,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(PlayerTestTags.TOOL_ROW),
        verticalAlignment = Alignment.Top,
    ) {
        actions.onOpenSpeed?.let { onClick ->
            ToolCell(
                label = stringResource(R.string.audiobook_player_tool_speed),
                tag = PlayerTestTags.TOOL_SPEED,
                onClick = onClick,
            ) {
                Text(
                    text = rateLabel(rate),
                    style = uiTextStyle(19, FontWeight.SemiBold, palette.ink2),
                )
            }
        }
        actions.onOpenCarMode?.let { onClick ->
            ToolCell(
                label = stringResource(R.string.audiobook_player_tool_car_mode),
                tag = PlayerTestTags.TOOL_CAR_MODE,
                onClick = onClick,
            ) {
                ToolIcon(R.drawable.ic_audiobook_player_car)
            }
        }
        actions.onOpenSleep?.let { onClick ->
            ToolCell(
                label = stringResource(R.string.audiobook_player_tool_sleep),
                tag = PlayerTestTags.TOOL_SLEEP,
                onClick = onClick,
            ) {
                ToolIcon(R.drawable.ic_audiobook_player_moon)
            }
        }
        actions.onAddBookmark?.let { onClick ->
            ToolCell(
                label = stringResource(R.string.audiobook_player_tool_bookmark),
                tag = PlayerTestTags.TOOL_BOOKMARK,
                onClick = onClick,
            ) {
                ToolIcon(R.drawable.ic_reader_bookmark)
            }
        }
        val sync = actions.syncHere
        if (sync != null && hasEbook) {
            ToolCell(
                label = sync.label,
                tag = PlayerTestTags.TOOL_SYNC,
                enabled = sync.enabled,
                disabledDescription = sync.disabledDescription,
                onClick = sync.onClick,
            ) {
                ToolIcon(R.drawable.ic_audiobook_player_sync)
            }
        }
    }
}

/**
 * A rate for the speed cell: whole rates drop the decimals ("1x", "2x"), others keep up to two ("1.25x", "1.5x"), in the
 * locale's digits (iOS `rateLabel`).
 */
internal fun rateLabel(rate: Double, locale: Locale = Locale.getDefault()): String {
    if (rate == floor(rate)) {
        return String.format(locale, "%d", rate.toLong()) + "x"
    }
    val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_EVEN
    }
    return format.format(rate) + "x"
}

@Composable
private fun ToolIcon(iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = OmnibusTheme.palette.ink2,
    )
}

/**
 * One tool cell: its glyph over its label, sharing the row's width equally. A disabled cell is dimmed, not clickable, and
 * speaks [disabledDescription] after its label.
 */
@Composable
private fun RowScope.ToolCell(
    label: String,
    tag: String,
    enabled: Boolean = true,
    disabledDescription: String? = null,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier
            .weight(1f)
            .testTag(tag)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                if (!enabled && disabledDescription != null) {
                    contentDescription = "$label, $disabledDescription"
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            glyph()
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = label,
            style = uiTextStyle(11, FontWeight.Normal, palette.ink2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
