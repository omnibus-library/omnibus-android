package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the two segments of the masthead rule. */
internal object MastheadTestTags {
    const val ACCENT_RULE = "masthead-accent-rule"
    const val HAIRLINE = "masthead-hairline"
}

/**
 * The top of a tab root: a wordmark row with [trailing] controls, then the large [title] over an
 * accent-led hairline.
 *
 * The whole block is one accessibility element with the heading role, so a screen reader announces
 * the title once, as the screen's heading, rather than reading each row separately.
 *
 * @param title The screen title, shown in display type.
 * @param trailing Controls at the end of the wordmark row, laid out left to right 8 dp apart.
 */
@Composable
fun Masthead(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val palette = OmnibusTheme.palette

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.lg)
            .semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Image rather than Icon, so the mark keeps its own colours and is never tinted.
            Image(
                painter = painterResource(R.drawable.omnibus_stoat),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = displayTextStyle(16, palette.ink2, FontWeight.Medium)
                    .copy(letterSpacing = 0.3.sp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
        Text(
            text = title,
            style = displayTextStyle(42, palette.ink0),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .testTag(MastheadTestTags.ACCENT_RULE)
                    .size(28.dp, 1.5.dp)
                    .background(palette.accent),
            )
            Box(
                Modifier
                    .testTag(MastheadTestTags.HAIRLINE)
                    .weight(1f)
                    .height(0.5.dp)
                    .background(palette.line2),
            )
        }
    }
}

/**
 * The circular add control on the masthead. Its neutral fill keeps it quiet beside the title, and
 * the accent glyph marks it as the one action.
 *
 * @param onAddBooks Called once per tap, after a light haptic tick.
 */
@Composable
fun MastheadAddButton(
    onAddBooks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.masthead_add_books)

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(palette.bg2)
            .border(0.5.dp, palette.line2, CircleShape)
            .clickable(role = Role.Button, onClickLabel = null) {
                // VirtualKey is the light key-tap tick, matching iOS's light impact.
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onAddBooks()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add_scan),
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
@PreviewLightDark
private fun MastheadPreview() {
    OmnibusTheme {
        Masthead(title = "Library") {
            MastheadAddButton(onAddBooks = {})
        }
    }
}

@Composable
@PreviewLightDark
private fun MastheadWithTrailingPreview() {
    OmnibusTheme {
        Masthead(title = "Library") {
            OfflinePillStandIn()
            MastheadAddButton(onAddBooks = {})
        }
    }
}

/** A stand-in for the later offline pill, so the trailing slot's spacing can be previewed. */
@Composable
private fun OfflinePillStandIn() {
    val palette = OmnibusTheme.palette

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(palette.bg2)
            .border(0.5.dp, palette.line2, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(palette.warn))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Offline",
            style = uiTextStyle(12, FontWeight.Medium, palette.warn),
        )
    }
}
