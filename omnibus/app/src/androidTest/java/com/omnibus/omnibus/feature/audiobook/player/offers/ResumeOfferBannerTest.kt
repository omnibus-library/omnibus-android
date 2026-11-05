package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResumeOfferBannerTest {

    @get:Rule
    val rule = createComposeRule()

    private val deviceTitle = "Listened further elsewhere"
    private val deviceDetail = "Another device left off at 1:42:10."

    // #166 TC-71: P-SYNC-6, R1, R6, R10: the banner shows its title, detail, Go and Dismiss
    @Test
    fun bannerShowsTitleDetailGoAndDismiss() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ResumeOfferBanner(title = deviceTitle, detail = deviceDetail, onGo = {}, onDismiss = {})
            }
        }

        rule.onNodeWithTag(ResumeOfferTags.BANNER).assertIsDisplayed()
        rule.onNodeWithTag(ResumeOfferTags.TITLE, useUnmergedTree = true).assertTextEquals(deviceTitle)
        rule.onNodeWithTag(ResumeOfferTags.DETAIL, useUnmergedTree = true).assertTextEquals(deviceDetail)
        rule.onNodeWithTag(ResumeOfferTags.GO).assertIsDisplayed().assertTextEquals("Go")
        rule.onNodeWithTag(ResumeOfferTags.DISMISS).assertIsDisplayed()
    }

    // #166 TC-72: R2, R7, R11: Go and Dismiss are buttons, and each calls only its own callback
    @Test
    fun goAndDismissAreClickableAndDoNotCrossFire() {
        var goCount = 0
        var dismissCount = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ResumeOfferBanner(
                    title = deviceTitle,
                    detail = deviceDetail,
                    onGo = { goCount++ },
                    onDismiss = { dismissCount++ },
                )
            }
        }

        rule.onNodeWithTag(ResumeOfferTags.GO)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
        rule.onNodeWithTag(ResumeOfferTags.GO).performClick()
        rule.waitForIdle()
        assertEquals(1, goCount)
        assertEquals(0, dismissCount)

        rule.onNodeWithTag(ResumeOfferTags.DISMISS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
        rule.onNodeWithTag(ResumeOfferTags.DISMISS).performClick()
        rule.waitForIdle()
        assertEquals(1, goCount)
        assertEquals(1, dismissCount)
    }

    // #166 TC-73: P-A11Y-4: Dismiss is spoken as "Dismiss" and is a button
    @Test
    fun dismissHasAnAccessibleLabel() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ResumeOfferBanner(title = deviceTitle, detail = deviceDetail, onGo = {}, onDismiss = {})
            }
        }

        rule.onNodeWithContentDescription("Dismiss")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasClickAction())
    }

    // #166 TC-74: P-A11Y-1, R11: the title and detail are one polite live region
    @Test
    fun titleAndDetailAreOneLiveRegionNode() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ResumeOfferBanner(title = deviceTitle, detail = deviceDetail, onGo = {}, onDismiss = {})
            }
        }

        val text = rule.onNodeWithTag(ResumeOfferTags.TEXT)
        text.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val spoken = text.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue(spoken.contains(deviceTitle))
        assertTrue(spoken.contains(deviceDetail))
    }
}
