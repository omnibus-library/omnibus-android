package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
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

    // #166 TC-78: R9, R16, P-SYNC-8: Retry on the error banner fires onRetry once
    @Test
    fun errorBannerRetryFiresOnRetry() {
        val callbacks = Callbacks()
        setSlot({ PlayerBanner.Error(PlaybackError.StoppedAfterRetries) }, callbacks)

        rule.onNodeWithTag(PlayerTestTags.RETRY).performClick()
        rule.waitForIdle()
        assertEquals(1, callbacks.retry)
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
