package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Reader destination for the test book. */
private val readerTo = BookDetailDestination.Reader(testDetailBook().uuid)

/** The Player destination for the test book on file 7. */
private val playerTo = BookDetailDestination.Player(testDetailBook().uuid, 7L)

/**
 * Renders [BookActionBar] for [model], with the test book's colours, in dark theme. The model is read from [model] so a
 * test can swap it in one composition. [extras] is #99's slot, and defaults to nothing.
 */
private fun ComposeContentTestRule.setBar(
    model: State<ActionBarModel>,
    reducedMotion: Boolean = true,
    onNavigate: (BookDetailDestination) -> Unit = {},
    extras: @Composable RowScope.() -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            BookActionBar(
                model = model.value,
                colors = actionBarColors(testDetailBook()),
                onNavigate = onNavigate,
                modifier = Modifier.fillMaxWidth(),
                reducedMotion = reducedMotion,
                extras = extras,
            )
        }
    }
}

private fun primary(label: ActionLabel, fill: Float = 0.3f, destination: BookDetailDestination = readerTo) =
    ActionBarModel.Primary(label, fill, destination)

class BookActionBarTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-43
    @Test
    fun issue98_tc43_labelTextForEveryLabel() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.StartReading))
        rule.setBar(model)

        val cases = listOf(
            ActionLabel.StartReading to detailText(R.string.book_detail_actionbar_start_reading),
            ActionLabel.ContinueReading(4) to detailText(R.string.book_detail_actionbar_continue_chapter, 4),
            ActionLabel.ContinueReading(null) to detailText(R.string.book_detail_actionbar_continue_reading),
            ActionLabel.ReadAgain to detailText(R.string.book_detail_actionbar_read_again),
            ActionLabel.StartListening to detailText(R.string.book_detail_actionbar_start_listening),
            ActionLabel.ContinueListening to detailText(R.string.book_detail_actionbar_continue_listening),
            ActionLabel.ListenAgain to detailText(R.string.book_detail_actionbar_listen_again),
        )
        for ((label, text) in cases) {
            rule.runOnIdle { model.value = primary(label) }
            rule.onNodeWithTag(ActionBarTestTags.LABEL, useUnmergedTree = true).assertTextEquals(text)
            rule.onNodeWithTag(ActionBarTestTags.BUTTON).assertContentDescriptionEquals(text)
        }
    }

    // TC-44
    @Test
    fun issue98_tc44_primaryIsAFullWidthPillAtLeast52dpHigh() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4), fill = 0.3f))
        rule.setBar(model)

        rule.onNodeWithTag(ActionBarTestTags.BUTTON).assertHeightIsAtLeast(52.dp)
        val root = rule.onRoot().bounds()
        val lowFillHeight = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds().height
        assertTrue(rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds().width > root.width * 0.8f)
        assertEquals(1, rule.onNodeWithTag(ActionBarTestTags.LABEL, useUnmergedTree = true).textLayout().lineCount)

        rule.runOnIdle { model.value = primary(ActionLabel.ContinueReading(4), fill = 1f) }
        val fullHeight = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds().height
        assertEquals(lowFillHeight, fullHeight, 1f)
        assertEquals(1, rule.onNodeWithTag(ActionBarTestTags.LABEL, useUnmergedTree = true).textLayout().lineCount)
    }

    // TC-45
    @Test
    fun issue98_tc45_longLabelStaysOneLine() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.StartReading))
        rule.setBar(model)
        val startHeight = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds().height

        rule.runOnIdle { model.value = primary(ActionLabel.ContinueReading(123456789)) }
        rule.onAllNodesWithTag(ActionBarTestTags.LABEL, useUnmergedTree = true).assertCountEquals(1)
        assertEquals(1, rule.onNodeWithTag(ActionBarTestTags.LABEL, useUnmergedTree = true).textLayout().lineCount)

        val button = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds()
        assertEquals(startHeight, button.height, 1f)
        val root = rule.onRoot().bounds()
        assertTrue(button.left >= root.left && button.right <= root.right)

        val icon = rule.onNodeWithTag(ActionBarTestTags.ICON, useUnmergedTree = true).bounds()
        assertTrue(icon.left >= button.left && icon.right <= button.right)
        assertTrue(icon.top >= button.top && icon.bottom <= button.bottom)

        rule.onNodeWithTag(ActionBarTestTags.BUTTON)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_actionbar_continue_chapter, 123456789))
    }

    // TC-46
    @Test
    fun issue98_tc46_tapNavigatesOncePerTapToTheDestination() {
        val calls = mutableListOf<BookDetailDestination>()
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4), destination = readerTo))
        rule.setBar(model, reducedMotion = true, onNavigate = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
        rule.waitForIdle()
        rule.runOnIdle { model.value = primary(ActionLabel.ContinueListening, destination = playerTo) }
        rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
        rule.waitForIdle()

        assertEquals(listOf(readerTo, playerTo, playerTo, playerTo), calls)
    }

    // TC-47
    @Test
    fun issue98_tc47_buttonIsOneMergedNodeDescribedByItsLabel() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4)))
        rule.setBar(model)
        val cases = listOf(
            ActionLabel.ContinueReading(4) to detailText(R.string.book_detail_actionbar_continue_chapter, 4),
            ActionLabel.ContinueListening to detailText(R.string.book_detail_actionbar_continue_listening),
        )
        for ((label, text) in cases) {
            rule.runOnIdle { model.value = primary(label, destination = readerTo) }
            rule.onNodeWithTag(ActionBarTestTags.BUTTON)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
                .assertContentDescriptionEquals(text)
            rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
                .assertCountEquals(1)
            rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertCountEquals(1)
        }
    }

    // TC-48
    @Test
    fun issue98_tc48_noCopyIsNotAButtonAndShowsBothTexts() {
        val calls = mutableListOf<BookDetailDestination>()
        val model = mutableStateOf<ActionBarModel>(ActionBarModel.NoCopy(physical = true))
        rule.setBar(model, onNavigate = { calls += it })

        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_physical_copy))
            .assertHeightIsAtLeast(52.dp)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<BookDetailDestination>(), calls)

        rule.runOnIdle { model.value = ActionBarModel.NoCopy(physical = false) }
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_no_copy))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
    }

    // TC-49
    @Test
    fun issue98_tc49_fillIsExposedThroughSemantics() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4), fill = 0f))
        rule.setBar(model, reducedMotion = true)
        fun fill() = rule.onNodeWithTag(ActionBarTestTags.BUTTON).fetchSemanticsNode().config[ActionBarFillKey]

        assertEquals(0f, fill(), 1e-4f)
        rule.runOnIdle { model.value = primary(ActionLabel.ContinueReading(4), fill = 0.5f) }
        assertEquals(0.5f, fill(), 1e-4f)
        rule.runOnIdle { model.value = primary(ActionLabel.ReadAgain, fill = 1f) }
        assertEquals(1f, fill(), 1e-4f)

        rule.runOnIdle { model.value = primary(ActionLabel.ContinueReading(4), fill = 0.2f) }
        rule.runOnIdle { model.value = primary(ActionLabel.ContinueReading(4), fill = 0.8f) }
        rule.waitForIdle()
        assertEquals(0.8f, fill(), 1e-4f)
    }

    // TC-50
    @Test
    fun issue98_tc50_fillAnimatesWhenMotionIsAllowed() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4), fill = 0.2f))
        rule.setBar(model, reducedMotion = false)
        rule.mainClock.autoAdvance = false
        fun fill() = rule.onNodeWithTag(ActionBarTestTags.BUTTON).fetchSemanticsNode().config[ActionBarFillKey]

        rule.runOnIdle { model.value = primary(ActionLabel.ContinueReading(4), fill = 0.8f) }
        rule.mainClock.advanceTimeBy(100L)
        val midway = fill()
        assertTrue("fill was $midway", midway > 0.2f && midway < 0.8f)

        rule.mainClock.advanceTimeBy(3_000L)
        assertEquals(0.8f, fill(), 0.01f)
    }

    // TC-51
    @Test
    fun issue98_tc51_readingLabelFlipsTheBookIconOnTap() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4)))
        rule.setBar(model, reducedMotion = false)
        rule.mainClock.autoAdvance = false
        fun scale() = rule.onNodeWithTag(ActionBarTestTags.ICON, useUnmergedTree = true)
            .fetchSemanticsNode().config[ActionBarIconScaleKey]

        assertEquals(1f, scale(), 1e-4f)
        rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
        rule.mainClock.advanceTimeBy(160L)
        val mid = scale()
        assertTrue("icon scale was $mid", mid > -1f && mid < 1f)

        rule.mainClock.advanceTimeBy(700L)
        assertEquals(1f, scale(), 1e-4f)

        rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
        rule.mainClock.advanceTimeBy(160L)
        assertTrue("second flip scale was ${scale()}", scale() < 1f)
    }

    // TC-52
    @Test
    fun issue98_tc52_listeningLabelsDoNotFlip() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.StartListening, destination = playerTo))
        rule.setBar(model, reducedMotion = false)
        rule.mainClock.autoAdvance = false
        fun scale() = rule.onNodeWithTag(ActionBarTestTags.ICON, useUnmergedTree = true)
            .fetchSemanticsNode().config[ActionBarIconScaleKey]

        for (label in listOf(ActionLabel.StartListening, ActionLabel.ContinueListening, ActionLabel.ListenAgain)) {
            rule.runOnIdle { model.value = primary(label, destination = playerTo) }
            assertEquals("$label before", 1f, scale(), 1e-4f)
            rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
            rule.mainClock.advanceTimeBy(160L)
            assertEquals("$label mid", 1f, scale(), 1e-4f)
            rule.mainClock.advanceTimeBy(700L)
            assertEquals("$label after", 1f, scale(), 1e-4f)
        }
    }

    // TC-53
    @Test
    fun issue98_tc53_reducedMotionDoesNotFlip() {
        val calls = mutableListOf<BookDetailDestination>()
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4), destination = readerTo))
        rule.setBar(model, reducedMotion = true, onNavigate = { calls += it })
        rule.mainClock.autoAdvance = false
        fun scale() = rule.onNodeWithTag(ActionBarTestTags.ICON, useUnmergedTree = true)
            .fetchSemanticsNode().config[ActionBarIconScaleKey]

        rule.onNodeWithTag(ActionBarTestTags.BUTTON).performClick()
        rule.mainClock.advanceTimeBy(700L)
        assertEquals(1f, scale(), 1e-4f)
        assertEquals(listOf(readerTo), calls)
    }

    // TC-54
    @Test
    fun issue98_tc54_extrasRenderBesideTheMainButton() {
        val showExtras = mutableStateOf(false)
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4)))
        rule.setBar(model, extras = {
            if (showExtras.value) {
                Box(Modifier.testTag("extra")) { Text("X") }
            }
        })

        val plainWidth = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds().width
        rule.onAllNodesWithTag("extra").assertCountEquals(0)

        rule.runOnIdle { showExtras.value = true }
        val button = rule.onNodeWithTag(ActionBarTestTags.BUTTON).bounds()
        val extra = rule.onNodeWithTag("extra").bounds()
        val root = rule.onRoot().bounds()
        assertTrue("extra starts after the button", extra.left >= button.right)
        assertEquals(button.center.y, extra.center.y, 1f)
        assertTrue("button narrower with extras", button.width < plainWidth)
        assertTrue("extra inside the root", extra.right <= root.right)
    }

    // TC-55
    @Test
    fun issue98_tc55_noCopyHidesExtras() {
        val model = mutableStateOf<ActionBarModel>(ActionBarModel.NoCopy(physical = false))
        rule.setBar(model, extras = { Box(Modifier.testTag("extra")) { Text("X") } })

        rule.onAllNodesWithTag(ActionBarTestTags.NO_COPY).assertCountEquals(1)
        rule.onAllNodesWithTag("extra").assertCountEquals(0)
    }

    // TC-61
    @Test
    fun issue98_tc61_fillGrowsFromTheStartEdgeInBothDirections() {
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ReadAgain, fill = 0.3f))
        val rtl = mutableStateOf(false)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                val direction = if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    BookActionBar(
                        model = model.value,
                        colors = actionBarColors(testDetailBook()),
                        onNavigate = {},
                        modifier = Modifier.fillMaxWidth(),
                        reducedMotion = true,
                    )
                }
            }
        }

        fun sums(): Pair<Int, Int> {
            val map = rule.onNodeWithTag(ActionBarTestTags.BUTTON).captureToImage().toPixelMap()
            val y = map.height / 2
            fun sum(x: Int): Int {
                val c = map[x, y]
                return ((c.red + c.green + c.blue) * 255).toInt()
            }
            return sum((map.width * 0.05f).toInt()) to sum((map.width * 0.95f).toInt())
        }

        val (ltrStart, ltrEnd) = sums()
        assertTrue("LTR: start edge should be the filled side ($ltrStart vs $ltrEnd)", ltrStart > ltrEnd)

        rule.runOnIdle { rtl.value = true }
        val (rtlStart, rtlEnd) = sums()
        assertTrue("RTL: end edge should be the filled side ($rtlStart vs $rtlEnd)", rtlEnd > rtlStart)
    }
}
