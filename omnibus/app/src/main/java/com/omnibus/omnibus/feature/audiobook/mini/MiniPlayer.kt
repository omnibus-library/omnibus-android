package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The mini player bar (P-MINI-1..4). It shows whenever a book is loaded in the controller, playing or paused, and fades
 * in and out with that. The shell mounts it above the tab bar, and the reader docks it at the bottom. Tapping the body
 * calls [onExpand] with the book's uuid, and the host opens the player.
 *
 * It needs no separate entry point for Immersive read (P-MINI-6): #99 starts the audio with
 * `AudiobookController.load(uuid, fileId, null, autoplay = true)` and then opens the reader, and the dock shows as soon
 * as the load starts.
 *
 * @param onExpand opens the player for the book with this uuid.
 * @param contentWindowInsets insets the row clears, such as the navigation bar when the bar is docked at the screen edge.
 * @param viewModel The bar's state. Hilt builds one per host entry.
 */
@Composable
fun MiniPlayer(
    onExpand: (bookUuid: String) -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets(0),
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val reduceMotion = rememberReducedMotion()
    // Keeps the last shown state, so the fade-out draws the bar it is removing instead of a blank one.
    val shown = remember { LastShown(uiState) }
    if (uiState.visible) shown.state = uiState

    // Fade only, with no size animation: the reader re-paginates on every height change, so the dock changes in one step.
    AnimatedVisibility(
        visible = uiState.visible,
        modifier = modifier,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(Motion.settle()),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(Motion.settle()),
    ) {
        MiniPlayerContent(
            uiState = shown.state,
            onExpand = onExpand,
            onSkipBack = viewModel::onSkipBack,
            onPlayPauseToggled = viewModel::onPlayPauseToggled,
            onStop = viewModel::onStop,
            contentWindowInsets = contentWindowInsets,
        )
    }
}

/** The last visible state, kept outside snapshot state so writing it during composition is safe. */
private class LastShown(var state: MiniPlayerUIState)

/** Test tags on the bar's own nodes, so tests can find them without depending on the layout. */
internal object MiniPlayerTestTags {
    const val BAR = "mini_player_bar"
    const val BODY = "mini_player_body"
    const val PROGRESS_TRACK = "mini_player_progress_track"
    const val PROGRESS_FILL = "mini_player_progress_fill"
    const val BACK_15 = "mini_player_back_15"
    const val PLAY_PAUSE = "mini_player_play_pause"
    const val STOP = "mini_player_stop"
}

/**
 * The bar itself, drawn for [uiState] whatever its visibility. The body is one tap target that opens the player; the
 * three buttons sit beside it and never reach [onExpand].
 */
@Composable
internal fun MiniPlayerContent(
    uiState: MiniPlayerUIState,
    onExpand: (bookUuid: String) -> Unit,
    onSkipBack: () -> Unit,
    onPlayPauseToggled: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets(0),
) {
    val palette = OmnibusTheme.palette
    val openLabel = stringResource(R.string.audiobook_mini_open_player)
    Column(
        modifier
            .fillMaxWidth()
            .background(palette.bg1.copy(alpha = 0.92f))
            .testTag(MiniPlayerTestTags.BAR),
    ) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.line2))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
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
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(contentWindowInsets)
                .padding(horizontal = Spacing.screen, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .testTag(MiniPlayerTestTags.BODY)
                    .clickable(onClickLabel = openLabel, role = Role.Button) { onExpand(uiState.bookUuid) }
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(
                    identity = uiState.cover,
                    modifier = Modifier.width(34.dp),
                    size = CoverSize.Sm,
                    cornerRadius = 4.dp,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = uiState.title,
                        style = uiTextStyle(13.5.sp, FontWeight.SemiBold, palette.ink0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    uiState.subtitle?.let {
                        Text(
                            text = it,
                            style = uiTextStyle(11, FontWeight.Normal, palette.ink3),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            MiniButton(
                iconRes = R.drawable.ic_audiobook_player_back_15,
                label = stringResource(R.string.audiobook_back_15),
                tag = MiniPlayerTestTags.BACK_15,
                onClick = onSkipBack,
            )
            MiniButton(
                iconRes = if (uiState.isPlaying) R.drawable.ic_audiobook_player_pause else R.drawable.ic_play,
                label = stringResource(if (uiState.isPlaying) R.string.audiobook_pause else R.string.audiobook_play),
                tag = MiniPlayerTestTags.PLAY_PAUSE,
                onClick = onPlayPauseToggled,
            )
            MiniButton(
                iconRes = R.drawable.ic_audiobook_mini_close,
                label = stringResource(R.string.audiobook_mini_stop),
                tag = MiniPlayerTestTags.STOP,
                onClick = onStop,
            )
        }
    }
}

/** One of the bar's buttons: a 48 dp touch target around a 20 dp icon. */
@Composable
private fun MiniButton(iconRes: Int, label: String, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag(tag)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = OmnibusTheme.palette.ink0,
        )
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerChapterPlayingPreview() {
    OmnibusTheme {
        MiniPlayerContent(
            uiState = MiniPlayerUIState(
                visible = true,
                bookUuid = "preview",
                cover = CoverIdentity(uuid = "preview", title = "The Left Hand of Darkness", creator = "Ursula K. Le Guin", hasCover = true),
                title = "The Left Hand of Darkness",
                subtitle = "Chapter 7: The Question of Sex",
                progress = 0.35f,
                isPlaying = true,
            ),
            onExpand = {},
            onSkipBack = {},
            onPlayPauseToggled = {},
            onStop = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerAuthorPausedPreview() {
    OmnibusTheme {
        MiniPlayerContent(
            uiState = MiniPlayerUIState(
                visible = true,
                bookUuid = "preview",
                cover = CoverIdentity(uuid = "preview", title = "The Left Hand of Darkness", creator = "Ursula K. Le Guin", hasCover = true),
                title = "The Left Hand of Darkness",
                subtitle = "Ursula K. Le Guin",
                progress = 0.6f,
                isPlaying = false,
            ),
            onExpand = {},
            onSkipBack = {},
            onPlayPauseToggled = {},
            onStop = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerLongTitlePreview() {
    OmnibusTheme {
        MiniPlayerContent(
            uiState = MiniPlayerUIState(
                visible = true,
                bookUuid = "preview",
                cover = CoverIdentity(uuid = "preview", title = "A Very Long Title That Keeps Going Well Past The Bar", creator = "An Author", hasCover = true),
                title = "A Very Long Title That Keeps Going Well Past The Bar Width And Must Stay On One Line",
                subtitle = "A Chapter Title That Is Also Rather Long For The Bar",
                progress = 0.5f,
                isPlaying = true,
            ),
            onExpand = {},
            onSkipBack = {},
            onPlayPauseToggled = {},
            onStop = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun MiniPlayerNoProgressPreview() {
    OmnibusTheme {
        MiniPlayerContent(
            uiState = MiniPlayerUIState(
                visible = true,
                bookUuid = "preview",
                cover = CoverIdentity(uuid = "preview", title = "The Left Hand of Darkness", creator = null, hasCover = true),
                title = "The Left Hand of Darkness",
                subtitle = null,
                progress = 0f,
                isPlaying = false,
            ),
            onExpand = {},
            onSkipBack = {},
            onPlayPauseToggled = {},
            onStop = {},
        )
    }
}
