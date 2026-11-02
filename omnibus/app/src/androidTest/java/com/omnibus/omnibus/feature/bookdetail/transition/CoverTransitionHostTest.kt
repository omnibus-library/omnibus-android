package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverTransitionHostTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val book = testDetailBook(id = 1)

    // TC-21
    @Test
    fun issue122_tc21_wrapKeepsTheConsumedInsetsOfTheEntries() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val wrapped = mutableStateOf(false)
        val reported = mutableStateOf<Int?>(null)
        val rootBottom = mutableStateOf<Int?>(null)

        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                val density = LocalDensity.current
                val stack = remember { mutableStateListOf<NavKey>(HarnessLibrary) }
                val nav: @Composable () -> Unit = {
                    NavDisplay(
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(WindowInsets.navigationBars),
                        backStack = stack,
                        onBack = {},
                        entryProvider = entryProvider {
                            entry<HarnessLibrary> {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .onConsumedWindowInsetsChanged { insets ->
                                            reported.value = insets.getBottom(density)
                                        },
                                )
                            }
                        },
                    )
                }
                val root = WindowInsets.navigationBars.getBottom(density)
                SideEffect { rootBottom.value = root }
                Column(Modifier.fillMaxSize()) {
                    if (wrapped.value) {
                        SharedTransitionLayout(Modifier.weight(1f)) {
                            ProvideBookCoverTransition(
                                this,
                                reducedMotion = false,
                                state = remember { BookCoverTransitionState() },
                            ) { nav() }
                        }
                    } else {
                        Box(Modifier.weight(1f)) { nav() }
                    }
                }
            }
        }
        // The first consumed-inset callback can report 0 before the window insets are ready, and it is not
        // repeated until something changes. So the unwrapped reading is taken after the wrap has been shown once.
        rule.waitForIdle()
        rule.runOnIdle { wrapped.value = true }
        rule.waitForIdle()
        val withWrap = reported.value
        rule.runOnIdle { wrapped.value = false }
        rule.waitForIdle()
        val withoutWrap = reported.value

        assertNotNull("the entry reports its consumed insets", withoutWrap)
        assertEquals("consumed navigation-bar inset with and without the host", withoutWrap, withWrap)
        assertEquals("the entry consumes the whole navigation-bar inset", rootBottom.value, withWrap)
    }

    // TC-24
    @Test
    fun issue122_tc24_movingCoverStaysInsideTheHostAboveTheTabBar() {
        val h = CoverTransitionHarness(listOf(book))
        rule.setContent { h.Content(withTabBar = true) }
        rule.waitForIdle()
        rule.onNodeWithTag(HARNESS_TAB_BAR).assertIsDisplayed()
        val hostBottom = rule.onNodeWithTag(HARNESS_HOST).rootRect().bottom

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(gridSourceTag("uuid-1"), useUnmergedTree = true).performClick()
        var sawTransit = false
        repeat(40) {
            rule.mainClock.advanceTimeByFrame()
            if (h.sharedScope.isTransitionActive) sawTransit = true
            val moving = rule.onAllNodesWithTag(BookCoverTransitionTestTags.stage(book.uuid), useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (moving.isNotEmpty()) {
                val bottom = moving.single().boundsInRoot.bottom
                assertTrue("moving cover bottom $bottom past host bottom $hostBottom", bottom <= hostBottom + 1f)
            }
            rule.onNodeWithTag(HARNESS_TAB_BAR).assertIsDisplayed()
        }
        assertTrue("the sampled frames should include the move", sawTransit)
    }
}
