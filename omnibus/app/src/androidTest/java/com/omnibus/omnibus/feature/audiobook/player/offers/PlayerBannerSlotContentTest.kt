package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.feature.audiobook.player.PlayerTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerBannerSlotContentTest {

    @get:Rule
    val rule = createComposeRule()

    /** Counts each callback, so a test can see which one a tap reached. */
    private class Callbacks {
        var retry = 0
        var goDevice = 0
        var dismissDevice = 0
        var goEbook = 0
        var dismissEbook = 0
    }

    /** Renders the slot for [banner], with [callbacks] counting each tap. */
    private fun setSlot(banner: () -> PlayerBanner?, callbacks: Callbacks) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerBannerSlotContent(
                    banner = banner(),
                    onRetry = { callbacks.retry++ },
                    onGoDevice = { callbacks.goDevice++ },
                    onDismissDevice = { callbacks.dismissDevice++ },
                    onGoEbook = { callbacks.goEbook++ },
                    onDismissEbook = { callbacks.dismissEbook++ },
                )
            }
        }
    }

    // #166 TC-78: R9, P-SYNC-8: the error banner shows its message and Retry, and no offer; Retry fires onRetry
    @Test
    fun errorBannerShowsRetryAndNoOffer() {
        val callbacks = Callbacks()
        setSlot({ PlayerBanner.Error(PlaybackError.StoppedAfterRetries) }, callbacks)

        rule.onNodeWithTag(PlayerTestTags.ERROR_BANNER).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.ERROR_MESSAGE).assertTextEquals(
            "Playback stopped and could not be resumed. Tap play to try again.",
        )
        rule.onAllNodesWithTag(ResumeOfferTags.BANNER).assertCountEquals(0)

        rule.onNodeWithTag(PlayerTestTags.RETRY).performClick()
        rule.waitForIdle()
        assertEquals(1, callbacks.retry)
    }

    // #166 TC-79: R1, R10, P-SYNC-6: the device offer shows its copy with the clock in hour form, and Go and Dismiss
    @Test
    fun deviceOfferShowsItsCopyAndClock() {
        setSlot({ PlayerBanner.DeviceOffer(3725.0) }, Callbacks())

        rule.onNodeWithTag(ResumeOfferTags.BANNER).assertIsDisplayed()
        rule.onNodeWithTag(ResumeOfferTags.TITLE, useUnmergedTree = true).assertTextEquals("Listened further elsewhere")
        rule.onNodeWithTag(ResumeOfferTags.DETAIL, useUnmergedTree = true)
            .assertTextEquals("Another device left off at 1:02:05.")
        rule.onNodeWithTag(ResumeOfferTags.GO).assertIsDisplayed()
        rule.onNodeWithTag(ResumeOfferTags.DISMISS).assertIsDisplayed()
    }

    // #166 TC-80: R6, R10, P-SYNC-AC5: the ebook title follows its direction when the state flips
    @Test
    fun ebookOfferTitleFollowsSourceAhead() {
        var banner by mutableStateOf<PlayerBanner?>(PlayerBanner.EbookOffer(65.0, sourceAhead = true))
        setSlot({ banner }, Callbacks())

        rule.onNodeWithTag(ResumeOfferTags.TITLE, useUnmergedTree = true).assertTextEquals("Read further in the ebook")
        rule.onNodeWithTag(ResumeOfferTags.DETAIL, useUnmergedTree = true)
            .assertTextEquals("Your reading maps to about 1:05.")

        rule.runOnIdle { banner = PlayerBanner.EbookOffer(65.0, sourceAhead = false) }
        rule.waitForIdle()

        rule.onNodeWithTag(ResumeOfferTags.TITLE, useUnmergedTree = true)
            .assertTextEquals("Re-reading earlier in the ebook")
        rule.onNodeWithTag(ResumeOfferTags.DETAIL, useUnmergedTree = true)
            .assertTextEquals("Your reading maps to about 1:05.")
    }

    // #166 TC-81: R9: a null banner draws nothing, and the slot draws again when a banner comes back
    @Test
    fun nullBannerShowsNothingAndSwaps() {
        var banner by mutableStateOf<PlayerBanner?>(null)
        setSlot({ banner }, Callbacks())

        rule.onAllNodesWithTag(ResumeOfferTags.BANNER).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.ERROR_BANNER).assertCountEquals(0)

        rule.runOnIdle { banner = PlayerBanner.DeviceOffer(3725.0) }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ResumeOfferTags.BANNER).assertCountEquals(1)

        rule.runOnIdle { banner = null }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ResumeOfferTags.BANNER).assertCountEquals(0)
    }

    // #166 TC-82: R2, R3, R7, R8: each tap reaches only the callback of its offer
    @Test
    fun actionsRouteToTheRightCallback() {
        val callbacks = Callbacks()
        var banner by mutableStateOf<PlayerBanner?>(PlayerBanner.DeviceOffer(3725.0))
        setSlot({ banner }, callbacks)

        rule.onNodeWithTag(ResumeOfferTags.GO).performClick()
        rule.onNodeWithTag(ResumeOfferTags.DISMISS).performClick()
        rule.waitForIdle()
        assertEquals(1, callbacks.goDevice)
        assertEquals(1, callbacks.dismissDevice)
        assertEquals(0, callbacks.goEbook)
        assertEquals(0, callbacks.dismissEbook)

        rule.runOnIdle { banner = PlayerBanner.EbookOffer(65.0, sourceAhead = true) }
        rule.waitForIdle()
        rule.onNodeWithTag(ResumeOfferTags.GO).performClick()
        rule.onNodeWithTag(ResumeOfferTags.DISMISS).performClick()
        rule.waitForIdle()
        assertEquals(1, callbacks.goEbook)
        assertEquals(1, callbacks.dismissEbook)
        assertEquals(1, callbacks.goDevice)
        assertEquals(1, callbacks.dismissDevice)
    }
}
