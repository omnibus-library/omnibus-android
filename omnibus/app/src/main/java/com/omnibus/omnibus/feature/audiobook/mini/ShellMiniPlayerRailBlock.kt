package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

// The full block's parts. They are summed into FullFixedHeight, so the height the variant is chosen by and the layout cannot drift.
private val TopPadding = 12.dp
private val CoverWidth = 48.dp
private val CoverHeight = 72.dp
private val CoverGap = 6.dp
private val BarHeight = 3.dp
private val BarGap = 8.dp
private val Target = 48.dp
private val BottomPadding = 8.dp

/** The full block's height without its percentage label (#286 R11). */
private val FullFixedHeight = TopPadding + CoverHeight + CoverGap + BarHeight + BarGap + Target + BottomPadding

/**
 * The rail's mini player block (#286 R8-R11): the full block when [space] fits it, else the compact one. [space] is the height
 * the rail's tabs leave below them, so the block never pushes a tab off. The cover, its progress bar and the percentage
 * label are dropped in the compact block; the close button and play/pause stay in both.
 */
@Composable
internal fun ShellMiniPlayerRailBlock(
    uiState: MiniPlayerUIState,
    space: Dp,
    actions: MiniPlayerActions,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val labelStyle = uiTextStyle(12, FontWeight.Medium, palette.ink1)
    // Measured, so the variant follows the font scale as well as the fixed parts.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelHeight = remember(measurer, labelStyle, density) {
        with(density) { measurer.measure("100%", labelStyle).size.height.toDp() }
    }
    val variant = railMiniPlayerVariant(space, FullFixedHeight + labelHeight)

    val openLabel = stringResource(R.string.audiobook_mini_open_player)
    val stopLabel = stringResource(R.string.audiobook_mini_stop)
    val playPauseLabel = stringResource(if (uiState.isPlaying) R.string.audiobook_pause else R.string.audiobook_play)
    val percentPhrase = uiState.percentListened?.let { stringResource(R.string.audiobook_mini_percent_listened, it) }
    val percentText = uiState.percentListened?.let { stringResource(R.string.audiobook_mini_percent, it) }
    val description = listOfNotNull(uiState.title, uiState.subtitle, percentPhrase).joinToString(", ")

    Box(modifier.fillMaxWidth().testTag(MiniPlayerTestTags.BAR)) {
        when (variant) {
            RailMiniPlayerVariant.Full -> Box(Modifier.fillMaxWidth().testTag(MiniPlayerTestTags.RAIL_FULL)) {
                Column(
                    Modifier.fillMaxWidth().padding(top = TopPadding, bottom = BottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(CoverWidth, CoverHeight)
                            .clickable(onClickLabel = openLabel, role = Role.Button) { actions.onExpand(uiState.bookUuid) }
                            .semantics { contentDescription = description }
                            .testTag(MiniPlayerTestTags.RAIL_COVER),
                    ) {
                        BookCover(
                            identity = uiState.cover,
                            modifier = Modifier.fillMaxSize(),
                            size = CoverSize.Sm,
                            cornerRadius = 4.dp,
                            keepsAspect = false,
                        )
                    }
                    Spacer(Modifier.height(CoverGap))
                    if (uiState.percentListened != null) {
                        Box(
                            Modifier
                                .width(CoverWidth)
                                .height(BarHeight)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(palette.line2)
                                .testTag(MiniPlayerTestTags.PROGRESS_TRACK),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(uiState.progress)
                                    .background(palette.accent)
                                    .testTag(MiniPlayerTestTags.PROGRESS_FILL),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(BarHeight))
                    }
                    Spacer(Modifier.height(BarGap))
                    PlayPausePill(uiState.isPlaying, playPauseLabel, actions.onPlayPauseToggled)
                    if (percentText != null) {
                        Text(percentText, style = labelStyle, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
                // Placed after the cover, so the close target wins the taps where they overlap. Its circle sits on the cover's corner.
                CloseButton(
                    Modifier.align(Alignment.TopEnd),
                    circle = 22.dp,
                    circleOffset = DpOffset(8.dp, (-12).dp),
                    label = stopLabel,
                    onClick = actions.onStop,
                )
            }
            RailMiniPlayerVariant.Compact -> Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(MiniPlayerTestTags.RAIL_COMPACT),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CloseButton(
                    Modifier,
                    circle = 24.dp,
                    circleOffset = DpOffset.Zero,
                    label = stopLabel,
                    onClick = actions.onStop,
                )
                PlayPausePill(uiState.isPlaying, playPauseLabel, actions.onPlayPauseToggled)
                if (percentText != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Target)
                            .clickable(onClickLabel = openLabel, role = Role.Button) { actions.onExpand(uiState.bookUuid) }
                            .semantics(mergeDescendants = true) { contentDescription = description }
                            .testTag(MiniPlayerTestTags.RAIL_PERCENT),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Text(percentText, style = labelStyle, modifier = Modifier.clearAndSetSemantics {})
                    }
                }
            }
        }
    }
}

/**
 * The play/pause control: a neutral 56x32dp pill, the rail's entry shape, in a full-width 48dp target. [label] is its
 * spoken name, so it reads as play or pause by the state.
 */
@Composable
private fun PlayPausePill(isPlaying: Boolean, label: String, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(Target)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(MiniPlayerTestTags.PLAY_PAUSE),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(56.dp, 32.dp).background(palette.line2, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_audiobook_player_pause else R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.ink0,
            )
        }
    }
}

/**
 * The stop button: a 48dp target holding a round × of [circle], offset by [circleOffset] from the target's centre. The
 * offset is how the full block puts it on the cover's corner, and `Modifier.offset` mirrors in RTL.
 */
@Composable
private fun CloseButton(
    modifier: Modifier,
    circle: Dp,
    circleOffset: DpOffset,
    label: String,
    onClick: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier
            .size(Target)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(MiniPlayerTestTags.STOP),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(circleOffset.x, circleOffset.y)
                .size(circle)
                .clip(CircleShape)
                .background(palette.bg0)
                .border(0.5.dp, palette.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_audiobook_mini_close),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = palette.ink0,
            )
        }
    }
}
