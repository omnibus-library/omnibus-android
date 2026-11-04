package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.feature.bookdetail.stage.BookStageWash
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The player's layout, without its ViewModel (R5 to R18). Everything is drawn on the book's wash over a fixed `bg0`
 * floor. A downward drag on the stage moves the surface, and only the stage takes that drag, so the controls can't
 * dismiss the player.
 *
 * @param uiState What to show.
 * @param actions The controls' actions. A null action hides its control.
 * @param onClose Called to collapse the player: the close button, a dismiss drag, OK on the offline dialog, or the
 * book being unloaded.
 * @param banner The banner slot, drawn above the cover.
 * @param reducedMotion Snaps the cover's scale and the dismiss spring instead of animating them.
 */
@Composable
internal fun PlayerScreenContent(
    uiState: PlayerUIState,
    actions: PlayerActions,
    onClose: () -> Unit,
    onPlayPauseToggled: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeek: (bookSeconds: Double) -> Unit,
    modifier: Modifier = Modifier,
    banner: @Composable () -> Unit = {},
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    val lightGround = isLightGround(palette.bg0)
    val dismiss = rememberPlayerDismissState()

    Box(modifier.fillMaxSize().background(palette.bg0).testTag(PlayerTestTags.ROOT)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dismiss.offsetDp * dismiss.density
                    alpha = 1f - dismiss.progress * 0.3f
                }
                .testTag(PlayerTestTags.SURFACE),
        ) {
            BookStageWash(
                identity = uiState.cover,
                lightGround = lightGround,
                modifier = Modifier.matchParentSize(),
                reducedMotion = reducedMotion,
            )
            when (uiState.gate) {
                PlayerGate.Checking -> Unit
                PlayerGate.Offline -> PlayerOfflineDialog(title = uiState.title, onDismiss = onClose)
                PlayerGate.Open -> Column(Modifier.fillMaxSize()) {
                    PlayerTopBar(
                        hasEbook = uiState.hasEbook,
                        sleepTimer = uiState.sleepTimer,
                        actions = actions,
                        onClose = onClose,
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = Spacing.screen),
                    )
                    Box(Modifier.padding(horizontal = Spacing.md)) {
                        banner()
                    }
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .playerDismissDrag(dismiss, reducedMotion, onClose)
                            .testTag(PlayerTestTags.STAGE),
                    ) {
                        val coverMax = coverHeightDp(
                            bandHeightDp = maxHeight.value,
                            widthDp = maxWidth.value - 2 * Spacing.screen.value,
                        ).dp
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlayerCover(
                                identity = uiState.cover,
                                isPlaying = uiState.isPlaying,
                                isPreparing = uiState.isPreparing,
                                isBuffering = uiState.isBuffering,
                                reducedMotion = reducedMotion,
                                modifier = Modifier.weight(1f, fill = false).heightIn(max = coverMax),
                            )
                            Spacer(Modifier.height(Spacing.md))
                            Titles(title = uiState.title, author = uiState.author)
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screen)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = Spacing.xs)
                            .testTag(PlayerTestTags.CONTROLS),
                    ) {
                        val openChapters = actions.onOpenChapters
                        val chapterTitle = uiState.chapterTitle
                        if (openChapters != null && uiState.hasChapters && chapterTitle != null) {
                            ChapterRow(title = chapterTitle, onClick = openChapters)
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        PlayerScrubber(
                            spanStartSeconds = uiState.spanStartSeconds,
                            spanLengthSeconds = uiState.spanLengthSeconds,
                            spanPositionSeconds = uiState.spanPositionSeconds,
                            durationSeconds = uiState.durationSeconds,
                            rate = uiState.rate,
                            hasChapters = uiState.hasChapters,
                            accent = bookAccent(uiState.cover.tone, lightGround).tint,
                            onSeek = onSeek,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        PlayerTransport(
                            isPlaying = uiState.isPlaying,
                            canPreviousChapter = uiState.canPreviousChapter,
                            canNextChapter = uiState.canNextChapter,
                            onPreviousChapter = onPreviousChapter,
                            onSkipBack = onSkipBack,
                            onPlayPauseToggled = onPlayPauseToggled,
                            onSkipForward = onSkipForward,
                            onNextChapter = onNextChapter,
                        )
                        Spacer(Modifier.height(Spacing.xl))
                        PlayerToolRow(rate = uiState.rate, hasEbook = uiState.hasEbook, actions = actions)
                    }
                }
            }
        }
        LaunchedEffect(uiState.leave) {
            if (uiState.leave) onClose()
        }
    }
}

/** The title (at most two lines) and the author (one line), centred under the cover. */
@Composable
private fun Titles(title: String, author: String?) {
    val palette = OmnibusTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            modifier = Modifier.testTag(PlayerTestTags.TITLE),
            style = displayTextStyle(26, palette.ink0),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (author != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = author,
                modifier = Modifier.testTag(PlayerTestTags.AUTHOR),
                style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The current chapter's title with a list icon. Tapping it opens the chapter list. */
@Composable
private fun ChapterRow(title: String, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.CHAPTER_ROW)
            .clickable(
                onClickLabel = stringResource(R.string.audiobook_player_chapters_action),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_contents),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = palette.ink2,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = title,
            modifier = Modifier.testTag(PlayerTestTags.CHAPTER_TITLE),
            style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val previewCover = CoverIdentity(uuid = "preview", title = "The Long Tide", creator = "Ada Marlowe", hasCover = false)

private fun previewState(
    isPlaying: Boolean = true,
    isPreparing: Boolean = false,
    gate: PlayerGate = PlayerGate.Open,
    hasChapters: Boolean = true,
    error: PlaybackError? = null,
    sleepTimer: SleepTimerState? = null,
) = PlayerUIState(
    gate = gate,
    cover = previewCover,
    title = "The Long Tide",
    author = "Ada Marlowe",
    hasEbook = true,
    chapterTitle = if (hasChapters) "004" else null,
    hasChapters = hasChapters,
    spanStartSeconds = if (hasChapters) 1000.0 else 0.0,
    spanLengthSeconds = if (hasChapters) 600.0 else 7200.0,
    spanPositionSeconds = 100.0,
    durationSeconds = 7200.0,
    canPreviousChapter = hasChapters,
    canNextChapter = hasChapters,
    isPlaying = isPlaying,
    isPreparing = isPreparing,
    error = error,
    sleepTimer = sleepTimer,
)

@Composable
private fun PreviewPlayer(uiState: PlayerUIState, actions: PlayerActions = PlayerActions(), banner: @Composable () -> Unit = {}) {
    OmnibusTheme {
        PlayerScreenContent(
            uiState = uiState,
            actions = actions,
            onClose = {},
            onPlayPauseToggled = {},
            onSkipBack = {},
            onSkipForward = {},
            onPreviousChapter = {},
            onNextChapter = {},
            onSeek = {},
            banner = banner,
        )
    }
}

@PreviewLightDark
@Composable
private fun PlayerScreenPlayingPreview() {
    PreviewPlayer(
        uiState = previewState(),
        actions = PlayerActions(
            onOpenChapters = {},
            onOpenSpeed = {},
            onOpenCarMode = {},
            onOpenSleep = {},
            onAddBookmark = {},
            onOpenBookmarks = {},
            onOpenBook = {},
            syncHere = PlayerToolButton(label = "Sync here", enabled = true, onClick = {}),
        ),
    )
}

@PreviewLightDark
@Composable
private fun PlayerScreenPausedNoChaptersPreview() {
    PreviewPlayer(uiState = previewState(isPlaying = false, hasChapters = false))
}

@PreviewLightDark
@Composable
private fun PlayerScreenLoadingPreview() {
    PreviewPlayer(uiState = previewState(isPreparing = true))
}

@PreviewLightDark
@Composable
private fun PlayerScreenErrorPreview() {
    PreviewPlayer(
        uiState = previewState(error = PlaybackError.StoppedAfterRetries),
        banner = { PlayerErrorBanner(error = PlaybackError.StoppedAfterRetries, onRetry = {}) },
    )
}

@PreviewLightDark
@Composable
private fun PlayerScreenOfflinePreview() {
    PreviewPlayer(uiState = previewState(gate = PlayerGate.Offline))
}
