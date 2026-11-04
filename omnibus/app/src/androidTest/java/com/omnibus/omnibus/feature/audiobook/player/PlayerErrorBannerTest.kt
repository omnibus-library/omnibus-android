package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerErrorBannerTest {

    @get:Rule
    val rule = createComposeRule()

    // R16, P-PLAY-9, P-PLAY-10: each error's copy, with Retry offered
    @Test
    fun bannerShowsTheCopyForEachError() {
        var error by mutableStateOf<PlaybackError>(PlaybackError.NoAudioParts)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerErrorBanner(error = error, onRetry = {})
            }
        }

        val cases = listOf(
            PlaybackError.NoAudioParts to "No audio parts.",
            PlaybackError.TimelineFailed to "Could not build the audio timeline.",
            PlaybackError.PartUnreadable to "An audio part could not be read.",
            PlaybackError.Server("Boom") to "Boom",
            PlaybackError.Server(null) to "The server couldn't load this audiobook.",
            PlaybackError.Server("  ") to "The server couldn't load this audiobook.",
            PlaybackError.StoppedAfterRetries to "Playback stopped and could not be resumed. Tap play to try again.",
        )
        for ((next, expected) in cases) {
            rule.runOnIdle { error = next }
            rule.waitForIdle()
            rule.onNodeWithTag(PlayerTestTags.ERROR_MESSAGE).assertTextEquals(expected)
            rule.onNodeWithText("Retry").assertIsDisplayed()
        }
    }

    // R16: Retry calls onRetry once
    @Test
    fun retryCallsOnRetry() {
        var retries = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerErrorBanner(error = PlaybackError.TimelineFailed, onRetry = { retries++ })
            }
        }

        rule.onNodeWithTag(PlayerTestTags.RETRY).performClick()
        rule.waitForIdle()
        assertEquals(1, retries)
    }

    // R18, P-A11Y-4: the message is a polite live region, and Retry is a button
    @Test
    fun bannerIsAPoliteLiveRegion() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerErrorBanner(error = PlaybackError.PartUnreadable, onRetry = {})
            }
        }

        rule.onNodeWithTag(PlayerTestTags.ERROR_MESSAGE).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        rule.onNodeWithTag(PlayerTestTags.RETRY).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
    }
}
