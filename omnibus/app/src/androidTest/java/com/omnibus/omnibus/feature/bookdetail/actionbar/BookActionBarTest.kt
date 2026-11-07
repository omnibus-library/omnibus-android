package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * test can swap it in one composition.
 */
private fun ComposeContentTestRule.setBar(
    model: State<ActionBarModel>,
    onNavigate: (BookDetailDestination) -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            BookActionBar(
                model = model.value,
                colors = actionBarColors(testDetailBook()),
                onNavigate = onNavigate,
                modifier = Modifier.fillMaxWidth(),
                reducedMotion = true,
            )
        }
    }
}

private fun primary(label: ActionLabel, destination: BookDetailDestination = readerTo) =
    ActionBarModel.Primary(label, 0.3f, destination)

class BookActionBarTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-46
    @Test
    fun issue98_tc46_tapNavigatesOncePerTapToTheDestination() {
        val calls = mutableListOf<BookDetailDestination>()
        val model = mutableStateOf<ActionBarModel>(primary(ActionLabel.ContinueReading(4), destination = readerTo))
        rule.setBar(model, onNavigate = { calls += it })

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

        rule.onNodeWithTag(ActionBarTestTags.BUTTON)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assertContentDescriptionEquals(detailText(R.string.book_detail_actionbar_continue_chapter, 4))
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assertCountEquals(1)
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertCountEquals(1)
    }

    // TC-48
    @Test
    fun issue98_tc48_noCopyIsNotAButton() {
        val model = mutableStateOf<ActionBarModel>(ActionBarModel.NoCopy(physical = true))
        rule.setBar(model)

        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
    }
}
