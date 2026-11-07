package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Callback counts and the inputs of [PlayerTransport], so tests can change them between frames. */
private class TransportInputs {
    var isPlaying by mutableStateOf(true)
    var canPrevious by mutableStateOf(true)
    var canNext by mutableStateOf(true)
    var previous = 0
    var back = 0
    var toggles = 0
    var forward = 0
    var next = 0
    val haptics = mutableListOf<HapticFeedbackType>()
}

private class RecordingHaptics(private val taps: MutableList<HapticFeedbackType>) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        taps += hapticFeedbackType
    }
}

private fun ComposeContentTestRule.setTransport(inputs: TransportInputs) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(LocalHapticFeedback provides RecordingHaptics(inputs.haptics)) {
                Box(Modifier.fillMaxSize()) {
                    PlayerTransport(
                        isPlaying = inputs.isPlaying,
                        canPreviousChapter = inputs.canPrevious,
                        canNextChapter = inputs.canNext,
                        onPreviousChapter = { inputs.previous++ },
                        onSkipBack = { inputs.back++ },
                        onPlayPauseToggled = { inputs.toggles++ },
                        onSkipForward = { inputs.forward++ },
                        onNextChapter = { inputs.next++ },
                    )
                }
            }
        }
    }
}

/** Callbacks, labels, enabled states and haptics. PlayerTransportScreenshotTest covers how the row looks. */
class PlayerTransportTest {

    @get:Rule
    val rule = createComposeRule()

    // P-SCR-10: each control calls its own callback once
    @Test
    fun eachControlCallsItsCallbackOnce() {
        val inputs = TransportInputs()
        rule.setTransport(inputs)

        rule.onNodeWithTag(PlayerTestTags.PREVIOUS).performClick()
        rule.onNodeWithTag(PlayerTestTags.BACK_15).performClick()
        rule.onNodeWithTag(PlayerTestTags.PLAY_PAUSE).performClick()
        rule.onNodeWithTag(PlayerTestTags.FORWARD_30).performClick()
        rule.onNodeWithTag(PlayerTestTags.NEXT).performClick()
        rule.waitForIdle()

        assertEquals(1, inputs.previous)
        assertEquals(1, inputs.back)
        assertEquals(1, inputs.toggles)
        assertEquals(1, inputs.forward)
        assertEquals(1, inputs.next)
    }

    // P-SCR-10: the disc is labelled by what a tap will do
    @Test
    fun discLabelFollowsPlayState() {
        val inputs = TransportInputs()
        inputs.isPlaying = false
        rule.setTransport(inputs)

        rule.onNodeWithContentDescription("Play").performClick()
        rule.waitForIdle()
        assertEquals(1, inputs.toggles)

        rule.runOnIdle { inputs.isPlaying = true }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.waitForIdle()
        assertEquals(2, inputs.toggles)
    }

    // P-SCR-AC4, P-PLAY-3-5: the next stepper disables on the last chapter, and previous stays on
    @Test
    fun nextDisabledOnLastChapter() {
        val inputs = TransportInputs()
        inputs.canNext = false
        rule.setTransport(inputs)

        rule.onNodeWithTag(PlayerTestTags.NEXT).assertIsNotEnabled()
        rule.onNodeWithTag(PlayerTestTags.NEXT).performClick()
        rule.onNodeWithTag(PlayerTestTags.PREVIOUS).assertIsEnabled()
        rule.waitForIdle()
        assertEquals(0, inputs.next)
    }

    // AND-UI-4: an enabled press gives one light tap; a disabled one gives none
    @Test
    fun enabledPressesGiveAHapticAndDisabledOnesDoNot() {
        val inputs = TransportInputs()
        inputs.canPrevious = false
        inputs.canNext = false
        rule.setTransport(inputs)

        rule.onNodeWithTag(PlayerTestTags.PREVIOUS).performClick()
        rule.onNodeWithTag(PlayerTestTags.NEXT).performClick()
        rule.waitForIdle()
        assertEquals(0, inputs.haptics.size)

        rule.onNodeWithTag(PlayerTestTags.BACK_15).performClick()
        rule.onNodeWithTag(PlayerTestTags.PLAY_PAUSE).performClick()
        rule.waitForIdle()
        assertEquals(listOf(HapticFeedbackType.VirtualKey, HapticFeedbackType.VirtualKey), inputs.haptics)
    }
}
