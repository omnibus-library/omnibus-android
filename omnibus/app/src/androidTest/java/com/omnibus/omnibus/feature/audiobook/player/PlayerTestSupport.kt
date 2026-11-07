package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The sleep pill the tests stand in for the real one. */
internal const val PILL_TAG = "test_sleep_pill"

/**
 * A playing, open book with chapters and an ebook, and a running state for each control. Tests override what they
 * check.
 */
internal fun fullUiState(
    gate: PlayerGate = PlayerGate.Open,
    title: String = "The Long Tide",
    author: String? = "Ada Marlowe",
    chapterTitle: String? = "004",
    hasChapters: Boolean = true,
    hasEbook: Boolean = true,
    spanStartSeconds: Double = 1000.0,
    spanLengthSeconds: Double = 600.0,
    spanPositionSeconds: Double = 100.0,
    durationSeconds: Double = 7200.0,
    rate: Double = 1.0,
    isPlaying: Boolean = true,
    isPreparing: Boolean = false,
    isBuffering: Boolean = false,
    canPreviousChapter: Boolean = true,
    canNextChapter: Boolean = true,
    error: PlaybackError? = null,
    sleepTimer: SleepTimerState? = null,
    leave: Boolean = false,
): PlayerUIState = PlayerUIState(
    gate = gate,
    leave = leave,
    cover = CoverIdentity(uuid = "u", title = "The Long Tide", creator = "Ada Marlowe", hasCover = false),
    title = title,
    author = author,
    hasEbook = hasEbook,
    chapterTitle = chapterTitle,
    hasChapters = hasChapters,
    spanStartSeconds = spanStartSeconds,
    spanLengthSeconds = spanLengthSeconds,
    spanPositionSeconds = spanPositionSeconds,
    durationSeconds = durationSeconds,
    rate = rate,
    canPreviousChapter = canPreviousChapter,
    canNextChapter = canNextChapter,
    isPlaying = isPlaying,
    isPreparing = isPreparing,
    isBuffering = isBuffering,
    error = error,
    sleepTimer = sleepTimer,
)

/** Counts how often each control's action ran. Every action is set, with a stand-in sleep pill. */
internal class RecordedActions {
    var chapters = 0
    var speed = 0
    var sleep = 0
    var carMode = 0
    var bookmark = 0
    var sync = 0
    var openBookmarks = 0
    var openBook = 0

    fun actions(): PlayerActions = PlayerActions(
        onOpenChapters = { chapters++ },
        onOpenSpeed = { speed++ },
        onOpenSleep = { sleep++ },
        sleepPill = { Text("pill", Modifier.testTag(PILL_TAG)) },
        onOpenCarMode = { carMode++ },
        onAddBookmark = { bookmark++ },
        onOpenBookmarks = { openBookmarks++ },
        syncHere = PlayerToolButton(label = "Sync here", enabled = true, onClick = { sync++ }),
        onOpenBook = { openBook++ },
    )
}

/**
 * Sets [PlayerScreenContent] with live [uiState] and [actions], so a test can change them between frames. The content
 * fills the window, and the reduced-motion flag is always explicit.
 */
internal fun ComposeContentTestRule.setPlayer(
    uiState: State<PlayerUIState>,
    actions: State<PlayerActions> = mutableStateOf(PlayerActions()),
    onClose: () -> Unit = {},
    onSeek: (Double) -> Unit = {},
    onPlayPauseToggled: () -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    reducedMotion: Boolean = true,
    banner: @androidx.compose.runtime.Composable () -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            PlayerScreenContent(
                uiState = uiState.value,
                actions = actions.value,
                onClose = onClose,
                onPlayPauseToggled = onPlayPauseToggled,
                onSkipBack = onSkipBack,
                onSkipForward = onSkipForward,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                onSeek = onSeek,
                banner = banner,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** [setPlayer] for a fixed state. */
internal fun ComposeContentTestRule.setPlayer(
    uiState: PlayerUIState,
    actions: PlayerActions = PlayerActions(),
    onClose: () -> Unit = {},
    onSeek: (Double) -> Unit = {},
    reducedMotion: Boolean = true,
    banner: @androidx.compose.runtime.Composable () -> Unit = {},
) = setPlayer(
    uiState = mutableStateOf(uiState),
    actions = mutableStateOf(actions),
    onClose = onClose,
    onSeek = onSeek,
    reducedMotion = reducedMotion,
    banner = banner,
)

/** The node's bounds in the window's pixels. */
internal fun ComposeContentTestRule.boundsOf(tag: String): Rect =
    onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

/** The node's text, joined in order. */
internal fun SemanticsNodeInteraction.textOf(): String =
    fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(separator = "") { it.text }

/** Reads a `m:ss` or `h:mm:ss` label, with an optional " left" suffix, as seconds. */
internal fun clockSeconds(label: String): Int {
    val parts = label.removeSuffix(" left").split(":").map { it.toInt() }
    return parts.fold(0) { total, part -> total * 60 + part }
}
