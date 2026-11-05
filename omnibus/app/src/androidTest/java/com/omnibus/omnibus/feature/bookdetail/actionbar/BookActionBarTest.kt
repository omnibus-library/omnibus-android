package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
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

    // TC-55
    @Test
    fun issue98_tc55_noCopyHidesExtras() {
        val model = mutableStateOf<ActionBarModel>(ActionBarModel.NoCopy(physical = false))
        rule.setBar(model, extras = { Box(Modifier.testTag("extra")) { Text("X") } })

        rule.onAllNodesWithTag(ActionBarTestTags.NO_COPY).assertCountEquals(1)
        rule.onAllNodesWithTag("extra").assertCountEquals(0)
    }
}
