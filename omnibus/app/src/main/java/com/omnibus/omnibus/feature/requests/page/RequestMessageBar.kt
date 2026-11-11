package com.omnibus.omnibus.feature.requests.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.delay

/**
 * The page's message: what happened, and an Undo when the write can be taken back. The text goes at its deadline and
 * the Undo at its own, both counted from [now], so a bar that comes back waits only for what is left. A message with no
 * Undo is held longer when the reader's accessibility timeout asks for it.
 *
 * @param now The clock the message's deadlines are against.
 * @param canUndo Whether Undo can be pressed now. It is off while another write runs or the device is offline, so a
 * press that would be ignored looks ignored.
 */
@Composable
internal fun RequestMessageBar(
    message: RequestMessage,
    now: () -> Long,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val dismissed by rememberUpdatedState(onDismissed)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(message.id) {
        val left = message.textRemainingMillis(now())
        delay(
            if (message.undoRequestId == null) {
                accessibility?.calculateRecommendedTimeoutMillis(left, containsIcons = false, containsText = true, containsControls = false) ?: left
            } else {
                left
            },
        )
        dismissed()
    }
    var undoOpen by remember(message.id) { mutableStateOf(message.undoRemainingMillis(now()) > 0) }
    LaunchedEffect(message.id) {
        if (message.undoRequestId != null) {
            delay(message.undoRemainingMillis(now()))
            undoOpen = false
        }
    }
    Surface(
        modifier = modifier.widthIn(max = MESSAGE_MAX_WIDTH).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = palette.ink0,
        contentColor = palette.bg0,
        shape = RoundedCornerShape(Radius.md),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.text,
                style = uiTextStyle(14, FontWeight.Normal, palette.bg0),
                modifier = Modifier.weight(1f),
            )
            if (message.undoRequestId != null && undoOpen) {
                Text(
                    text = stringResource(R.string.requests_undo),
                    style = uiTextStyle(14, FontWeight.SemiBold, palette.bg0).copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier
                        .alpha(if (canUndo) 1f else UNDO_DISABLED_ALPHA)
                        .heightIn(min = 48.dp)
                        .clickable(enabled = canUndo, role = Role.Button, onClick = onUndo)
                        .padding(horizontal = Spacing.md)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}

private val MESSAGE_MAX_WIDTH = 560.dp

private const val UNDO_DISABLED_ALPHA = 0.45f
