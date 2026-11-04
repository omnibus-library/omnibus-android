package com.omnibus.omnibus.feature.audiobook.carmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Car mode is always drawn on black, in the dark palette, whichever theme the app is in. */
private val palette = OmnibusPalette.Atrium
private val tileColor = palette.bg1

/**
 * Car mode (P-CAR-1): the `CarMode` overlay of the player, drawn full screen on black over the player. It is not a
 * route. System back is handled by the player, which dismisses its overlay.
 *
 * @param onDismiss Leaves car mode: the ✕ (P-CAR-6).
 */
@Composable
fun CarMode(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CarModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    CarModeContent(
        uiState = uiState,
        onDismiss = onDismiss,
        onSkipBack = viewModel::onSkipBack,
        onSkipForward = viewModel::onSkipForward,
        onPlayPauseToggled = viewModel::onPlayPauseToggled,
        onPreviousChapter = viewModel::onPreviousChapter,
        onNextChapter = viewModel::onNextChapter,
        modifier = modifier,
    )
}

/**
 * Car mode's layout, without its ViewModel. The screen stays on while this is composed (P-CAR-5, AND-UI-3). The tiles
 * share whatever height is left under the title, so they fill a small phone and a large one alike (P-CAR-3).
 *
 * @param uiState What to show.
 * @param onDismiss Called by the ✕.
 */
@Composable
internal fun CarModeContent(
    uiState: CarModeUIState,
    onDismiss: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPlayPauseToggled: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeepScreenOn()
    val haptics = LocalHapticFeedback.current
    fun press(action: () -> Unit): () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        action()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // The player is still composed underneath, so the overlay takes every touch that lands on it.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag(CarModeTestTags.ROOT)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            CloseButton(onClick = onDismiss)
        }
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = uiState.title,
            modifier = Modifier.fillMaxWidth().testTag(CarModeTestTags.TITLE),
            style = uiTextStyle(28, FontWeight.Bold, palette.ink0),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = stringResource(
                R.string.audiobook_time_left,
                PlaybackTimeFormat.hoursMinutes(uiState.bookSecondsLeftAtRate),
            ),
            modifier = Modifier.fillMaxWidth().testTag(CarModeTestTags.TIME_LEFT),
            style = uiTextStyle(18, FontWeight.Normal, palette.ink2),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.weight(SPACE_WEIGHT))
        Column(
            Modifier.weight(TILES_WEIGHT).fillMaxWidth().testTag(CarModeTestTags.TILES),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                RowTile(
                    iconRes = R.drawable.ic_audiobook_player_back_15,
                    label = stringResource(R.string.audiobook_back_15),
                    tag = CarModeTestTags.BACK_15,
                    enabled = true,
                    onClick = press(onSkipBack),
                )
                RowTile(
                    iconRes = R.drawable.ic_audiobook_player_forward_30,
                    label = stringResource(R.string.audiobook_forward_30),
                    tag = CarModeTestTags.FORWARD_30,
                    enabled = true,
                    onClick = press(onSkipForward),
                )
            }
            Tile(
                iconRes = if (uiState.isPlaying) R.drawable.ic_audiobook_player_pause else R.drawable.ic_play,
                label = stringResource(if (uiState.isPlaying) R.string.audiobook_pause else R.string.audiobook_play),
                tag = CarModeTestTags.PLAY_PAUSE,
                enabled = true,
                onClick = press(onPlayPauseToggled),
                modifier = Modifier.weight(PLAY_ROW_WEIGHT).fillMaxWidth(),
                container = palette.accent,
                tint = Color.Black,
            )
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                RowTile(
                    iconRes = R.drawable.ic_audiobook_player_previous_chapter,
                    label = stringResource(R.string.audiobook_previous_chapter),
                    tag = CarModeTestTags.PREVIOUS,
                    enabled = uiState.canPreviousChapter,
                    onClick = press(onPreviousChapter),
                )
                RowTile(
                    iconRes = R.drawable.ic_audiobook_player_next_chapter,
                    label = stringResource(R.string.audiobook_next_chapter),
                    tag = CarModeTestTags.NEXT,
                    enabled = uiState.canNextChapter,
                    onClick = press(onNextChapter),
                )
            }
        }
    }
}

// The free height under the title splits into a gap above the tiles and the tiles; the play row is taller than the
// others (P-CAR-3). Weights, never fixed sizes, so the layout fits every screen.
private const val SPACE_WEIGHT = 0.4f
private const val TILES_WEIGHT = 1f
private const val PLAY_ROW_WEIGHT = 1.4f

/** Keeps the screen on while it is composed, and puts the view's flag back when it leaves (AND-UI-3). */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
}

/** The round ✕ at the top right, a 38 dp disc in a 48 dp touch target like the player's top-bar buttons. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val label = stringResource(R.string.audiobook_carmode_leave)
    Box(
        Modifier
            .testTag(CarModeTestTags.CLOSE)
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
                painter = painterResource(R.drawable.ic_audiobook_carmode_close),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.ink1,
            )
        }
    }
}

/** One rounded tile. In a row it takes an equal share of the width; a disabled tile dims its glyph. */
@Composable
private fun RowScope.RowTile(
    iconRes: Int,
    label: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) = Tile(
    iconRes = iconRes,
    label = label,
    tag = tag,
    enabled = enabled,
    onClick = onClick,
    modifier = Modifier.weight(1f).fillMaxHeight(),
)

@Composable
private fun Tile(
    iconRes: Int,
    label: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    container: Color = tileColor,
    tint: Color = palette.ink0,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Radius.xl))
            .background(container)
            .testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = if (enabled) tint else palette.ink3,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun CarModePreview() {
    OmnibusTheme(darkTheme = true) {
        CarModeContent(
            uiState = CarModeUIState(title = "004", bookSecondsLeftAtRate = 73_500.0, canPreviousChapter = true, canNextChapter = true),
            onDismiss = {},
            onSkipBack = {},
            onSkipForward = {},
            onPlayPauseToggled = {},
            onPreviousChapter = {},
            onNextChapter = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun CarModeNoChaptersPreview() {
    OmnibusTheme(darkTheme = true) {
        CarModeContent(
            uiState = CarModeUIState(title = "The Long Tide", bookSecondsLeftAtRate = 5_400.0, isPlaying = true),
            onDismiss = {},
            onSkipBack = {},
            onSkipForward = {},
            onPlayPauseToggled = {},
            onPreviousChapter = {},
            onNextChapter = {},
        )
    }
}
