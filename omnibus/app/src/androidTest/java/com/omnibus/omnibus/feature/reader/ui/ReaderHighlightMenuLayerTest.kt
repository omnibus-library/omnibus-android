package com.omnibus.omnibus.feature.reader.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.HighlightMenu
import com.omnibus.omnibus.feature.reader.HighlightMenuEntry
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The highlight menu over the page: its swatches, Copy, the switcher, the outline, and the scrim that takes every touch
 * outside it. Every highlight is a fraction of the window, so the cases hold on the 320x640 AVD and on a phone.
 */
class ReaderHighlightMenuLayerTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** The window's size in dp, read from the root, so every highlight in a test is a fraction of it. */
    private fun window(): Pair<Float, Float> {
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        return (root.right - root.left).value to (root.bottom - root.top).value
    }

    /**
     * One highlight on the line at 40% of the window's height, from [left] to [right] as fractions of its width, with
     * [text] for Copy. Its CFI is made from its colour, so highlights of different colours never share one.
     */
    private fun highlight(
        width: Float,
        height: Float,
        color: HighlightColor,
        left: Float = 0.2f,
        right: Float = 0.8f,
        text: String? = TEXT,
    ): HighlightMenuEntry = HighlightMenuEntry(
        cfiRange = "epubcfi(/6/2!/4/2/1:${color.ordinal},/4/2/1:9)",
        rects = listOf(
            PageRect(
                x = (width * left).toDouble(),
                y = (height * 0.4f).toDouble(),
                width = ((right - left) * width).toDouble(),
                height = (height * 0.05f).toDouble(),
            ),
        ),
        color = color,
        text = text,
        hasNote = false,
    )

    /** Sets the overlay over the page's [under], with [menu] held by the test and stepped by [onStep] as default. */
    private fun setOverlay(
        menu: MutableState<HighlightMenu?>,
        under: @Composable () -> Unit = {},
        onStep: (Int) -> Unit = { delta -> menu.value = menu.value?.stepped(delta) },
        onCopy: (String) -> Unit = {},
        onDelete: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    under()
                    menu.value?.let { current ->
                        ReaderHighlightMenuOverlay(
                            menu = current,
                            theme = ReaderTheme.Light,
                            onStep = onStep,
                            onCopy = onCopy,
                            onDelete = onDelete,
                            onDismiss = onDismiss,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    /** Shows the menu [build] makes for the window's size, once the window is laid out. */
    private fun showMenu(menu: MutableState<HighlightMenu?>, build: (width: Float, height: Float) -> HighlightMenu?) {
        rule.waitForIdle()
        val (width, height) = window()
        rule.runOnIdle { menu.value = build(width, height) }
        rule.waitForIdle()
    }

    private fun assertActive(position: String, swatch: String) {
        rule.onNodeWithTag(ReaderHighlightMenuTags.POSITION, useUnmergedTree = true)
            .assert(hasContentDescription(position))
        rule.onNodeWithContentDescription(swatch).assertIsSelected()
        rule.onNodeWithContentDescription("Previous highlight").assertIsEnabled()
        rule.onNodeWithContentDescription("Next highlight").assertIsEnabled()
    }

    // TC-25
    @Test
    fun issue299_tc25_theFiveSwatchesAreNamedButtonsWithOnlyTheActiveOneSelected() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        setOverlay(menu)

        HighlightColor.entries.forEach { active ->
            showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, active))) }

            val selected = SWATCH_NAMES.map { name ->
                rule.onNodeWithContentDescription(name).fetchSemanticsNode()
                    .config.getOrNull(SemanticsProperties.Selected) ?: false
            }
            assertEquals(
                "only the active colour should be selected, when $active is active",
                HighlightColor.entries.map { it == active },
                selected,
            )
        }
        SWATCH_NAMES.forEach {
            rule.onNodeWithContentDescription(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    // TC-27
    @Test
    fun issue299_tc27_copyHandsUpTheActiveText() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        val copies = mutableListOf<String>()
        setOverlay(menu, onCopy = { copies += it })
        showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber, text = TEXT))) }

        rule.onNodeWithContentDescription("Copy").performClick()
        rule.waitForIdle()

        assertEquals(listOf(TEXT), copies)
    }

    // TC-28
    @Test
    fun issue299_tc28_aHighlightWithoutTextHasNoCopy() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        setOverlay(menu)

        listOf<String?>(null, "   ").forEach { text ->
            showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber, text = text))) }

            rule.onAllNodesWithContentDescription("Copy").assertCountEquals(0)
            SWATCH_NAMES.forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed() }
        }
    }

    // TC-29
    @Test
    fun issue299_tc29_theSwitcherAndOutlineShowOnlyForSeveralHits() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        setOverlay(menu)

        showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber))) }
        rule.onAllNodesWithTag(ReaderHighlightMenuTags.POSITION, useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag(ReaderHighlightMenuTags.OUTLINE, useUnmergedTree = true).assertCountEquals(0)

        showMenu(menu) { width, height ->
            HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber), highlight(width, height, HighlightColor.Green)))
        }
        rule.onAllNodesWithTag(ReaderHighlightMenuTags.POSITION, useUnmergedTree = true).assertCountEquals(1)
        assertTrue(
            "the active highlight should be outlined",
            rule.onAllNodesWithTag(ReaderHighlightMenuTags.OUTLINE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // TC-30
    @Test
    fun issue299_tc30_theArrowsStepAndWrapAndTheCheckFollows() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        setOverlay(menu)
        showMenu(menu) { width, height ->
            HighlightMenu(
                listOf(
                    highlight(width, height, HighlightColor.Amber),
                    highlight(width, height, HighlightColor.Green),
                    highlight(width, height, HighlightColor.Blue),
                ),
            )
        }
        assertActive(position = "Highlight 1 of 3", swatch = "Amber highlight")

        rule.onNodeWithContentDescription("Next highlight").performClick()
        rule.waitForIdle()
        assertActive(position = "Highlight 2 of 3", swatch = "Green highlight")

        rule.onNodeWithContentDescription("Next highlight").performClick()
        rule.waitForIdle()
        assertActive(position = "Highlight 3 of 3", swatch = "Blue highlight")

        rule.onNodeWithContentDescription("Next highlight").performClick()
        rule.waitForIdle()
        assertActive(position = "Highlight 1 of 3", swatch = "Amber highlight")

        rule.onNodeWithContentDescription("Previous highlight").performClick()
        rule.waitForIdle()
        assertActive(position = "Highlight 3 of 3", swatch = "Blue highlight")
    }

    // TC-31
    @Test
    fun issue299_tc31_theScrimTakesEveryTouchOutsideTheMenuAndClosesIt() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        val dismissals = mutableListOf<Unit>()
        val taps = mutableListOf<Unit>()
        val drags = mutableListOf<Unit>()
        setOverlay(
            menu,
            under = { Box(Modifier.fillMaxSize().clickable { taps += Unit }.pointerInput(Unit) { detectDragGestures { _, _ -> drags += Unit } }) },
            onDismiss = { dismissals += Unit },
        )
        showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber))) }
        val size = rule.onRoot().fetchSemanticsNode().size
        val lowerEdge = size.height * 0.9f

        rule.onRoot().performTouchInput { click(Offset(size.width * 0.05f, lowerEdge)) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { click(Offset(size.width * 0.95f, lowerEdge)) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { click(Offset(size.width * 0.5f, lowerEdge)) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { swipe(Offset(size.width * 0.3f, lowerEdge), Offset(size.width * 0.7f, lowerEdge)) }
        rule.waitForIdle()

        assertEquals("each outside touch should close the menu once", 4, dismissals.size)
        assertEquals("no outside tap should reach the page", 0, taps.size)
        assertEquals("no outside drag should reach the page", 0, drags.size)
    }

    // TC-31
    @Test
    fun issue299_tc31_aTapOnTheMenuDoesNotCloseIt() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        val dismissals = mutableListOf<Unit>()
        val taps = mutableListOf<Unit>()
        setOverlay(
            menu,
            under = { Box(Modifier.fillMaxSize().clickable { taps += Unit }) },
            onDismiss = { dismissals += Unit },
        )
        showMenu(menu) { width, height ->
            HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber), highlight(width, height, HighlightColor.Green)))
        }

        rule.onNodeWithContentDescription("Green highlight").performTouchInput { click() }
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderHighlightMenuTags.MENU, useUnmergedTree = true).assertIsDisplayed()
        assertEquals("a touch on the menu should not close it", 0, dismissals.size)
        assertEquals("a touch on the menu should not reach the page", 0, taps.size)
    }

    // TC-14 (#300)
    @Test
    fun issue300_tc14_deleteShowsForEveryHighlightAfterCopy() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        setOverlay(menu)

        listOf<String?>(TEXT, null).forEach { text ->
            showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber, text = text))) }

            rule.onNodeWithContentDescription("Delete highlight").assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }

        showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber, text = TEXT))) }
        val copy = rule.onNodeWithContentDescription("Copy").getUnclippedBoundsInRoot()
        val delete = rule.onNodeWithContentDescription("Delete highlight").getUnclippedBoundsInRoot()
        assertTrue("Delete should sit after Copy", delete.left >= copy.right)
        val centreGap = abs((delete.top + delete.bottom).value / 2 - (copy.top + copy.bottom).value / 2)
        assertTrue("Delete should share Copy's row", centreGap < (delete.bottom - delete.top).value)
    }

    // TC-15 (#300)
    @Test
    fun issue300_tc15_deleteCallsBack() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        val deletes = mutableListOf<Unit>()
        setOverlay(menu, onDelete = { deletes += Unit })
        showMenu(menu) { width, height -> HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber))) }

        rule.onNodeWithContentDescription("Delete highlight").performClick()
        rule.waitForIdle()

        assertEquals("Delete should call back exactly once", 1, deletes.size)
    }

    // TC-32
    @Test
    fun issue299_tc32_longPressShowsEachIconsTooltip() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        setOverlay(menu)
        showMenu(menu) { width, height ->
            HighlightMenu(listOf(highlight(width, height, HighlightColor.Amber), highlight(width, height, HighlightColor.Green)))
        }

        // The Delete label is added by #300 (TC-14).
        listOf("Previous highlight", "Next highlight", "Copy", "Delete highlight").forEach { label ->
            rule.onNodeWithContentDescription(label).performTouchInput { longClick() }
            rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private companion object {
        const val TEXT = "Somewhere behind the wall"

        /** The swatches' names, in [HighlightColor] order. */
        val SWATCH_NAMES = listOf("Amber highlight", "Green highlight", "Blue highlight", "Rose highlight", "Violet highlight")
    }
}
