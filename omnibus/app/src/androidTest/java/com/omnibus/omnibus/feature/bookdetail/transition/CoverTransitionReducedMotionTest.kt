package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.feature.library.ui.LibraryTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CoverTransitionReducedMotionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val book = testDetailBook(id = 1)

    // TC-16
    @Test
    fun issue122_tc16_reducedMotionSetsUpNoSharedElement() {
        val h = CoverTransitionHarness(listOf(book), reducedMotion = true)
        rule.setContent { h.Content() }
        rule.waitForIdle()

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(LibraryTestTags.cover(1), useUnmergedTree = true).performClick()
        repeat(40) {
            rule.mainClock.advanceTimeByFrame()
            assertFalse("no shared transition under reduced motion", h.sharedScope.isTransitionActive)
            rule.onAllNodesWithTag(BookCoverTransitionTestTags.stage(book.uuid), useUnmergedTree = true)
                .assertCountEquals(0)
        }

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag(BookDetailTestTags.COVER).assertIsDisplayed()
    }

    // TC-17
    @Test
    fun issue122_tc17_defaultFollowsRememberReducedMotion() {
        var providerIsNull: Boolean? = null
        var reducedMotion: Boolean? = null

        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                SharedTransitionLayout(Modifier) {
                    ProvideBookCoverTransition(this) {
                        val transition = LocalBookCoverTransition.current
                        val reduced = rememberReducedMotion()
                        SideEffect {
                            providerIsNull = transition == null
                            reducedMotion = reduced
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        assertEquals("the provider is off exactly when reduced motion is on", reducedMotion, providerIsNull)
    }
}
