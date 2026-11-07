package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRatingInputTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The Your review card: its semantics, the write and delete controls, the delete confirmation and the unknown states.
 * MyReviewCardScreenshotTest covers how each state looks.
 */
class MyReviewCardTest {

    @get:Rule
    val rule = createComposeRule()

    /** Shows [model] as a card, with motion off. */
    private fun show(
        model: MyReviewModel,
        onRate: (Float?) -> Unit = {},
        onDelete: () -> Unit = {},
        onCompose: (() -> Unit)? = null,
    ) = rule.setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxWidth()) {
                MyReviewCard(model, Color.Red, onRate, onDelete, onCompose, reducedMotion = true)
            }
        }
    }

    private fun review(rating: Float? = null, text: String? = null) =
        Review(0L, "", false, rating, text, null, isSpoiler = false, updatedAtMillis = null)

    // TC-64 (R9, R13)
    @Test
    fun tc64_theHeadingIsAHeading() {
        show(MyReviewModel.Known(null))

        rule.onNodeWithTag(MyReviewTestTags.HEADING)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // TC-69 (R10, R14)
    @Test
    fun tc69_theWriteControlsOpenTheComposer() {
        var composed = 0
        show(MyReviewModel.Known(null), onCompose = { composed++ })

        rule.onNodeWithTag(MyReviewTestTags.PENCIL).performClick()
        rule.onNodeWithTag(MyReviewTestTags.PROMPT).performClick()
        assertEquals(2, composed)
    }

    // TC-71 (R15)
    @Test
    fun tc71_deleteAsksFirstAndOnlyAConfirmedDeleteCallsBack() {
        var deleted = 0
        show(MyReviewModel.Known(review(rating = 4f, text = "Body")), onDelete = { deleted++ })
        val title = detailText(R.string.book_detail_rate_delete_title)

        rule.onNodeWithTag(MyReviewTestTags.TRASH).performClick()
        rule.onNodeWithText(title).assertExists()

        rule.onNodeWithTag(MyReviewTestTags.DELETE_CANCEL).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertEquals(0, deleted)

        rule.onNodeWithTag(MyReviewTestTags.TRASH).performClick()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertEquals(0, deleted)

        rule.onNodeWithTag(MyReviewTestTags.TRASH).performClick()
        rule.onNodeWithTag(MyReviewTestTags.DELETE_CONFIRM).performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        assertEquals(1, deleted)
    }

    // TC-72 (R15)
    @Test
    fun tc72_theDeleteDialogSurvivesStateRestoration() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) {
                    MyReviewCard(
                        MyReviewModel.Known(review(rating = 4f, text = "Body")),
                        Color.Red,
                        onRate = {},
                        onDelete = {},
                        onCompose = null,
                        reducedMotion = true,
                    )
                }
            }
        }

        rule.onNodeWithTag(MyReviewTestTags.TRASH).performClick()
        rule.onNodeWithTag(MyReviewTestTags.DELETE_CONFIRM).assertExists()
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(MyReviewTestTags.DELETE_CONFIRM).assertExists()
    }

    // TC-73 (R16, AC5)
    @Test
    fun tc73_theUnknownStatesDisableTheControls() {
        var rated = 0
        show(MyReviewModel.Unknown(UnknownReason.Offline), onRate = { rated++ }, onCompose = {})

        rule.onNodeWithTag(StarRatingInputTestTags.ROW).assertIsNotEnabled()
        rule.onNodeWithTag(MyReviewTestTags.PENCIL).assertIsNotEnabled()
        rule.onNodeWithTag(StarRatingInputTestTags.ROW).performTouchInput { click(Offset(width * 0.5f, height / 2f)) }

        assertEquals(0, rated)
    }

    // TC-75 (R11, R15, R20, AC1, AC3, AC4)
    @Test
    fun tc75_aTapOnTheStarsCallsOnRate() {
        val rates = mutableListOf<Float?>()
        show(MyReviewModel.Known(review(rating = 4f)), onRate = { rates += it })

        rule.onNodeWithTag(StarRatingInputTestTags.ROW).performTouchInput {
            click(Offset(width * 0.95f, height / 2f))
        }

        assertEquals(listOf(5f), rates)
    }
}
