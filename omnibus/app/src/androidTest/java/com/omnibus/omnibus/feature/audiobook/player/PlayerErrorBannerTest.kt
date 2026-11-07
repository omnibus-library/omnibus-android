package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assert
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class PlayerErrorBannerTest {

    @get:Rule
    val rule = createComposeRule()

    // R18, P-A11Y-4: the message is a polite live region
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
    }
}
