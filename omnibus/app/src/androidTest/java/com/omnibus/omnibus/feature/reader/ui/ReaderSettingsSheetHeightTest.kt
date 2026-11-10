package com.omnibus.omnibus.feature.reader.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * How tall the reader's settings sheet is and how it moves. The activity draws edge to edge, as
 * the reader does. Heights are checked as shares of the sheet's window, so the cases hold on the
 * 320x640 CI AVD and on a phone.
 */
class ReaderSettingsSheetHeightTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** Hosts the sheet edge to edge and counts its dismissals. */
    private fun host(dismissals: IntArray = IntArray(1), changes: MutableList<ReaderSettings> = mutableListOf()) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    ReaderSettingsSheet(
                        settings = ReaderSettings(justify = false),
                        accent = readerSheetAccent(ReaderBookState.Ready(title = "Test Book"), OmnibusTheme.palette),
                        onSettingsChanged = { changes += it },
                        onDismiss = { dismissals[0]++ },
                    )
                }
            }
        }
    }

    /** The sheet is the window's only pane, and the pane's bounds follow the sheet as it moves. */
    private fun pane() = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))

    /** The height of the sheet's window, which fills the screen. */
    private fun windowHeight() = rule.onNode(isRoot() and hasAnyDescendant(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)))
        .getBoundsInRoot().height

    /** The share of the window the sheet covers, from its top edge down. */
    private fun visibleShare(): Float = 1f - pane().getBoundsInRoot().top / windowHeight()

    /** Drags slowly by [distance] px, then holds still before lifting, so the release has no fling. */
    private fun TouchInjectionScope.slowDrag(start: Offset, distance: Float) {
        down(start)
        repeat(20) { moveBy(Offset(0f, distance / 20), delayMillis = 20) }
        moveBy(Offset(0f, 1f), delayMillis = 200)
        up()
    }

    @Test
    fun theOpenSheetLeavesPartOfThePageVisibleBelowTheStatusBar() {
        var statusBarBottom = 0.dp
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            val density = LocalDensity.current
            statusBarBottom = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    ReaderSettingsSheet(
                        settings = ReaderSettings(),
                        accent = readerSheetAccent(ReaderBookState.Ready(title = "Test Book"), OmnibusTheme.palette),
                        onSettingsChanged = {},
                        onDismiss = {},
                    )
                }
            }
        }

        val sheetTop = pane().getBoundsInRoot().top
        assertTrue(
            "the sheet's top ($sheetTop) should sit below the status bar's bottom edge ($statusBarBottom)",
            sheetTop > statusBarBottom,
        )
    }

    @Test
    fun theSheetOpensAt92PercentOfTheWindow() {
        host()

        assertEquals(0.92f, visibleShare(), 0.01f)
    }

    @Test
    fun aSlowDragDownOnTheHeaderStopsAt40PercentOfTheWindow() {
        val dismissals = IntArray(1)
        host(dismissals)
        val distance = (0.92f - 0.40f) * windowHeight().value * rule.density.density

        pane().performTouchInput { slowDrag(Offset(centerX, 4.dp.toPx()), distance) }
        rule.waitForIdle()

        assertEquals(0.40f, visibleShare(), 0.02f)
        assertEquals(0, dismissals[0])
    }

    @Test
    fun aSlowDragDownOnTheControlsFromTheirTopStopsAt40PercentOfTheWindow() {
        host()
        val distance = (0.92f - 0.40f) * windowHeight().value * rule.density.density

        rule.onNodeWithTag(ReaderSettingsSheetTags.swatch(ReaderTheme.Sepia))
            .performTouchInput { slowDrag(center, distance) }
        rule.waitForIdle()

        assertEquals(0.40f, visibleShare(), 0.02f)
    }

    @Test
    fun theLowestControlStillWorksAtTheLowerStop() {
        val changes = mutableListOf<ReaderSettings>()
        host(changes = changes)
        val distance = (0.92f - 0.40f) * windowHeight().value * rule.density.density
        pane().performTouchInput { slowDrag(Offset(centerX, 4.dp.toPx()), distance) }
        rule.waitForIdle()
        assertEquals("the sheet should rest at its lower stop first", 0.40f, visibleShare(), 0.02f)

        rule.onNodeWithTag(ReaderSettingsSheetTags.JUSTIFY).performScrollTo().performClick()

        assertEquals(listOf(ReaderSettings(justify = true)), changes)
    }

    @Test
    fun theHeaderStaysOutsideTheScrollingControls() {
        host()

        val controls = rule.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag(ReaderSettingsSheetTags.JUSTIFY)))
        assertFalse(
            "Done should not scroll with the controls",
            hasAnyDescendant(hasTestTag(ReaderSettingsSheetTags.DONE)).matches(controls.fetchSemanticsNode()),
        )
    }

    /** Regression: scrolling the controls to the bottom made the sheet bounce up and down without end (#253). */
    @Test
    fun theSheetComesToRestAfterTheControlsAreScrolledToTheBottom() {
        host()
        val tolerance = 0.005f * windowHeight().value

        rule.mainClock.autoAdvance = false
        repeat(8) {
            rule.onNodeWithTag(ReaderSettingsSheetTags.SHEET).performTouchInput { swipeUp(durationMillis = 100) }
            rule.mainClock.advanceTimeBy(640)
        }
        val tops = (1..300).map {
            rule.mainClock.advanceTimeBy(16)
            pane().getBoundsInRoot().top.value
        }

        val spread = tops.max() - tops.min()
        assertTrue("the sheet's top still moved by ${spread}dp over the 4.8s after the last swipe", spread < tolerance)
    }
}
