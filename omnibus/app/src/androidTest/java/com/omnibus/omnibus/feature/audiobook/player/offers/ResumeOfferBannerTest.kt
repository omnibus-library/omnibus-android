package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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

    /** The lines a Text node lays out to, read from its text layout. */
    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        val getLayout = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        checkNotNull(getLayout) { "the node has no text layout" }
        getLayout(results)
        return results.single().lineCount
    }

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

    // #166 TC-75: P-A11Y-4, AC5: Go and Dismiss meet the 48 dp touch target (lower bounds only)
    @Test
    fun goAndDismissMeetTheMinimumTouchTarget() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ResumeOfferBanner(title = deviceTitle, detail = deviceDetail, onGo = {}, onDismiss = {})
            }
        }

        rule.onNodeWithTag(ResumeOfferTags.GO).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(ResumeOfferTags.DISMISS).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    // #166 TC-76: R1, R6, R11: a very long title and detail keep the layout intact
    @Test
    fun longTextDoesNotBreakTheLayout() {
        val longText = "word ".repeat(200)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ResumeOfferBanner(title = longText, detail = longText, onGo = {}, onDismiss = {})
            }
        }

        assertEquals(1, rule.onNodeWithTag(ResumeOfferTags.TITLE, useUnmergedTree = true).lineCount())
        assertTrue(rule.onNodeWithTag(ResumeOfferTags.DETAIL, useUnmergedTree = true).lineCount() <= 2)

        val banner = rule.onNodeWithTag(ResumeOfferTags.BANNER).fetchSemanticsNode().boundsInRoot
        val text = rule.onNodeWithTag(ResumeOfferTags.TEXT).fetchSemanticsNode().boundsInRoot
        val go = rule.onNodeWithTag(ResumeOfferTags.GO).fetchSemanticsNode().boundsInRoot
        val dismiss = rule.onNodeWithTag(ResumeOfferTags.DISMISS).fetchSemanticsNode().boundsInRoot

        rule.onNodeWithTag(ResumeOfferTags.GO).assertIsDisplayed()
        rule.onNodeWithTag(ResumeOfferTags.DISMISS).assertIsDisplayed()
        assertWithin(banner, text)
        assertWithin(banner, go)
        assertWithin(banner, dismiss)
        assertTrue("Go sits left of Dismiss", go.right <= dismiss.left)
        assertTrue("the text column does not overlap Go", text.right <= go.left)
    }

    // #166 TC-77: R11: the banner draws in both themes and fills the width of its parent
    @Test
    fun bannerRendersInBothThemesAndFillsWidth() {
        var darkTheme by mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxWidth()) {
                    ResumeOfferBanner(title = deviceTitle, detail = deviceDetail, onGo = {}, onDismiss = {})
                }
            }
        }

        rule.onNodeWithTag(ResumeOfferTags.BANNER).assertIsDisplayed()
        assertFillsRoot()

        rule.runOnIdle { darkTheme = false }
        rule.waitForIdle()

        rule.onNodeWithTag(ResumeOfferTags.BANNER).assertIsDisplayed()
        assertFillsRoot()
    }

    /** The banner's width equals the root's, within a pixel, so it fills its parent. */
    private fun assertFillsRoot() {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val banner = rule.onNodeWithTag(ResumeOfferTags.BANNER).fetchSemanticsNode().boundsInRoot.width
        assertEquals(root, banner, 1f)
    }

    /** Asserts [inner] lies within [outer], allowing for rounding. */
    private fun assertWithin(outer: Rect, inner: Rect) {
        assertTrue(inner.left >= outer.left - 1f)
        assertTrue(inner.top >= outer.top - 1f)
        assertTrue(inner.right <= outer.right + 1f)
        assertTrue(inner.bottom <= outer.bottom + 1f)
    }
}
