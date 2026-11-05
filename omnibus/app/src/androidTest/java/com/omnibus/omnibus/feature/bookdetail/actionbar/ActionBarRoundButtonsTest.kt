package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Oklch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private val book = testDetailBook(formats = listOf("epub", "m4b"))
private val readTo = RoundAction.Read(BookDetailDestination.Reader(book.uuid))
private val immersive = RoundAction.ImmersiveRead(BookDetailDestination.Reader(book.uuid), 7L, loadsAudio = true)
private val listenOn = RoundAction.Listen(true, ListenTarget.Open(BookDetailDestination.Player(book.uuid, 7L)))
private val listenOff = RoundAction.Listen(false, ListenTarget.Open(BookDetailDestination.Player(book.uuid, 7L)))

/**
 * Renders the round buttons for [actions] in a row, in dark theme. The list is read from [actions] so a test can swap it
 * in one composition. Each tap is recorded in the callback the test passes.
 */
private fun ComposeContentTestRule.setRoundButtons(
    actions: State<List<RoundAction>>,
    reducedMotion: Boolean = true,
    onRead: (RoundAction.Read) -> Unit = {},
    onListen: (RoundAction.Listen) -> Unit = {},
    onImmersiveRead: (RoundAction.ImmersiveRead) -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Row(Modifier.fillMaxWidth()) {
                RoundButtons(
                    actions = actions.value,
                    listenColors = listenButtonColors(Oklch(0.62, 0.12, 40.0), lightGround = false),
                    onRead = onRead,
                    onListen = onListen,
                    onImmersiveRead = onImmersiveRead,
                    reducedMotion = reducedMotion,
                )
            }
        }
    }
}

class ActionBarRoundButtonsTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-9
    @Test
    fun issue99_tc9_readRoundButtonDescribedAndClickable() {
        val calls = mutableListOf<RoundAction.Read>()
        val actions = mutableStateOf<List<RoundAction>>(listOf(readTo, immersive))
        rule.setRoundButtons(actions, onRead = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_READ)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_listen_read))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onAllNodesWithContentDescription(detailText(R.string.book_detail_listen_listen), useUnmergedTree = true)
            .assertCountEquals(0)

        rule.onNodeWithTag(ActionBarTestTags.ROUND_READ).performClick()
        rule.waitForIdle()
        assertEquals(listOf(readTo), calls)
    }

    // TC-10
    @Test
    fun issue99_tc10_listenIsButtonWithFourBars() {
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOn, immersive))
        rule.setRoundButtons(actions)

        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_listen_listen))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        for (index in ListenBarRest.indices) {
            rule.onAllNodesWithTag("${ActionBarTestTags.LISTEN_BAR}-$index", useUnmergedTree = true).assertCountEquals(1)
        }
    }

    // TC-12
    @Test
    fun issue99_tc12_tapDoesNotWaitForPulse() {
        rule.mainClock.autoAdvance = false
        val calls = mutableListOf<RoundAction.Listen>()
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOn, immersive))
        rule.setRoundButtons(actions, reducedMotion = false, onListen = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).performClick()
        rule.waitForIdle()
        assertEquals(listOf(listenOn), calls)
    }

    // TC-13
    @Test
    fun issue99_tc13_reducedMotionNeverAnimatesBars() {
        rule.mainClock.autoAdvance = false
        val calls = mutableListOf<RoundAction.Listen>()
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOn, immersive))
        rule.setRoundButtons(actions, reducedMotion = true, onListen = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).performClick()
        rule.mainClock.advanceTimeBy(3_000L)
        rule.waitForIdle()

        for (index in ListenBarRest.indices) {
            val scale = rule.onNodeWithTag("${ActionBarTestTags.LISTEN_BAR}-$index", useUnmergedTree = true)
                .fetchSemanticsNode().config[ListenBarScaleKey]
            assertEquals(ListenBarRest[index], scale, 1e-4f)
        }
        assertEquals(listOf(listenOn), calls)
    }

    // TC-14
    @Test
    fun issue99_tc14_barsRestAtDesignHeights() {
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOn, immersive))
        rule.setRoundButtons(actions)

        val scales = ListenBarRest.indices.map { index ->
            rule.onNodeWithTag("${ActionBarTestTags.LISTEN_BAR}-$index", useUnmergedTree = true)
                .fetchSemanticsNode().config[ListenBarScaleKey]
        }
        assertEquals(listOf(0.3f, 1.0f, 0.55f, 0.8f), scales)
    }

    // TC-21
    @Test
    fun issue99_tc21_disabledListenIsDescribedAndInert() {
        val calls = mutableListOf<RoundAction.Listen>()
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOff, immersive))
        rule.setRoundButtons(actions, onListen = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_listen_listen_offline))
            .assertIsNotEnabled()
        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<RoundAction.Listen>(), calls)
    }

    // TC-22
    @Test
    fun issue99_tc22_listenReenablesWhenAvailable() {
        val calls = mutableListOf<RoundAction.Listen>()
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOff, immersive))
        rule.setRoundButtons(actions, onListen = { calls += it })
        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).assertIsNotEnabled()

        rule.runOnIdle { actions.value = listOf(listenOn, immersive) }
        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_listen_listen))
            .assertIsEnabled()
            .performClick()
        rule.waitForIdle()
        assertEquals(listOf(listenOn), calls)
    }

    // TC-23
    @Test
    fun issue99_tc23_immersiveNeverDisabled() {
        val calls = mutableListOf<RoundAction.ImmersiveRead>()
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOff, immersive))
        rule.setRoundButtons(actions, onImmersiveRead = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_IMMERSIVE).assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf(immersive), calls)
    }

    // TC-42
    @Test
    fun issue99_tc42_immersiveDescribedAndClickable() {
        val calls = mutableListOf<RoundAction.ImmersiveRead>()
        val actions = mutableStateOf<List<RoundAction>>(listOf(listenOn, immersive))
        rule.setRoundButtons(actions, onImmersiveRead = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_IMMERSIVE)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_listen_immersive_read))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        rule.waitForIdle()
        assertEquals(listOf(immersive), calls)
    }
}
