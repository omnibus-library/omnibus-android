package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The stage's rating button: its one description, and its taps. */
class StageRatingTest {

    @get:Rule
    val rule = createComposeRule()

    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    /** The plural average phrase, as the stage reads it. */
    private fun averagePhrase(average: String, count: Int) =
        resources.getQuantityString(R.plurals.book_detail_reviews_stage_average, count, average, count)

    /** The whole stage description: the state's phrase and "Opens Reviews.". */
    private fun description(phrase: String) = detailText(R.string.book_detail_reviews_stage_opens, phrase)

    private fun show(model: StageRatingModel, onClick: () -> Unit = {}) = rule.setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxWidth()) { StageRating(model, Color.Red, onClick = onClick) }
        }
    }

    /** Shows [initial] in one composition, and returns the holder that changes the model for a walk through states. */
    private fun showWalking(initial: StageRatingModel, onClick: () -> Unit): MutableState<StageRatingModel> {
        val model = mutableStateOf(initial)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) { StageRating(model.value, Color.Red, onClick = onClick) }
            }
        }
        return model
    }

    // TC-41 (AC1)
    @Test
    fun tc41_averageIsOneButtonWithItsDescription() {
        show(StageRatingModel.Average(stars = 4.5f, average = 4.3f, count = 3))

        rule.onNodeWithTag(ReviewsTestTags.STAGE_RATING)
            .assertContentDescriptionEquals(description(averagePhrase("4.3", 3)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // Accessibility reads the merged tree, where the stars are cleared into the stage's one node.
        rule.onAllNodes(hasContentDescription("out of 5 stars", substring = true)).assertCountEquals(0)
    }

    // TC-44 (AC1)
    @Test
    fun tc44_clickWorksInEveryState() {
        var clicks = 0
        val model = showWalking(StageRatingModel.Checking) { clicks++ }
        val states = listOf(
            StageRatingModel.Average(stars = 4f, average = 4f, count = 1),
            StageRatingModel.NoReviews,
            StageRatingModel.Checking,
            StageRatingModel.NeedsConnection,
            StageRatingModel.CouldNotLoad,
        )

        states.forEachIndexed { index, state ->
            rule.runOnIdle { model.value = state }
            rule.onNodeWithTag(ReviewsTestTags.STAGE_RATING).performClick()
            rule.waitForIdle()
            assertEquals(index + 1, clicks)
        }
    }
}
