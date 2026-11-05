package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRatingInputTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * The Your review card: its semantics, the write and delete controls, the delete confirmation, the unknown states and
 * motion. Sizes are minimums, never fixed. MyReviewCardScreenshotTest covers how each state looks.
 */
class MyReviewCardTest {

    @get:Rule
    val rule = createComposeRule()

    /** Shows [model] as a card. Motion is off unless a test asks for it, so the loading state does not breathe. */
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

    /** Shows the card and returns the holder that changes its model, for a walk through states. */
    private fun walk(
        initial: MyReviewModel,
        onRate: (Float?) -> Unit = {},
        onDelete: () -> Unit = {},
        onCompose: (() -> Unit)? = null,
        reducedMotion: Boolean = true,
    ): MutableState<MyReviewModel> {
        val model = mutableStateOf(initial)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) {
                    MyReviewCard(model.value, Color.Red, onRate, onDelete, onCompose, reducedMotion = reducedMotion)
                }
            }
        }
        return model
    }

    private fun review(
        rating: Float? = null,
        text: String? = null,
        notes: String? = null,
        spoiler: Boolean = false,
    ) = Review(0L, "", false, rating, text, notes, isSpoiler = spoiler, updatedAtMillis = null)

    private fun assertStateDescription(description: String) {
        rule.onNodeWithTag(StarRatingInputTestTags.ROW)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
    }

    // TC-64 (R9, R13)
    @Test
    fun tc64_aNewCardHasAHeadingAndUnratedStars() {
        show(MyReviewModel.Known(null))

        rule.onNodeWithTag(MyReviewTestTags.HEADING)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertStateDescription(detailText(R.string.book_detail_rate_stars_unrated))
    }

    // TC-64 (R9, R13)
    @Test
    fun tc64_theNoteAndTheStarStateFollowTheRating() {
        val model = walk(MyReviewModel.Known(review(rating = 4.5f)))

        rule.onNodeWithTag(MyReviewTestTags.NOTE)
            .assertTextEquals(detailText(R.string.book_detail_rate_note_rated, "4.5"))
        assertStateDescription(detailText(R.string.book_detail_rate_stars_state, "4.5"))

        rule.runOnIdle { model.value = MyReviewModel.Known(review(rating = 4f)) }
        rule.onNodeWithTag(MyReviewTestTags.NOTE).assertTextEquals(detailText(R.string.book_detail_rate_note_rated, "4"))
    }

    // TC-67 (R14)
    @Test
    fun tc67_aLongSpoilerTextShowsInFullWithoutAVeil() {
        val paragraphs = List(8) { "Paragraph $it, which goes on a while so that the review is long." }
        val markdown = paragraphs.joinToString("\n\n").replaceFirst("Paragraph 0", "Paragraph *zero*")
        show(MyReviewModel.Known(review(rating = 4f, text = markdown, notes = "Only mine", spoiler = true)))

        rule.onNodeWithTag(MyReviewTestTags.TEXT)
            .assertTextEquals(markdown.replace("*zero*", "zero"))
        rule.onNodeWithText(detailText(R.string.book_detail_reviews_read_more)).assertDoesNotExist()
        rule.onNodeWithText(detailText(R.string.book_detail_reviews_show_review)).assertDoesNotExist()
        rule.onNodeWithText(detailText(R.string.book_detail_reviews_spoiler_warning)).assertDoesNotExist()
    }

    // TC-69 (R10, R14)
    @Test
    fun tc69_withNoWritingTheControlsSayWriteAndPromptToWrite() {
        var composed = 0
        show(MyReviewModel.Known(null), onCompose = { composed++ })

        rule.onNodeWithTag(MyReviewTestTags.PENCIL)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_rate_write))
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
        rule.onNodeWithText(detailText(R.string.book_detail_rate_prompt)).assertExists()
        rule.onNodeWithTag(MyReviewTestTags.PROMPT).assertHeightIsAtLeast(44.dp)

        rule.onNodeWithTag(MyReviewTestTags.PENCIL).performClick()
        rule.onNodeWithTag(MyReviewTestTags.PROMPT).performClick()
        assertEquals(2, composed)
    }

    // TC-69 (R10, R14)
    @Test
    fun tc69_withTextTheControlsSayEditAndThePromptGoes() {
        show(MyReviewModel.Known(review(rating = 4f, text = "Body")), onCompose = {})

        rule.onNodeWithTag(MyReviewTestTags.PENCIL)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_rate_edit))
        rule.onNodeWithTag(MyReviewTestTags.PROMPT).assertDoesNotExist()
    }

    // TC-69 (R10, R14)
    @Test
    fun tc69_withOnlyNotesTheControlsSayEditAndThePromptStays() {
        show(MyReviewModel.Known(review(notes = "private")), onCompose = {})

        rule.onNodeWithTag(MyReviewTestTags.PENCIL)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_rate_edit))
        rule.onNodeWithTag(MyReviewTestTags.PROMPT).assertExists()
    }

    // TC-70 (R10)
    @Test
    fun tc70_theTrashShowsOnlyWithAReview() {
        val model = walk(MyReviewModel.Known(review(rating = 4f)), onCompose = {})

        rule.onNodeWithTag(MyReviewTestTags.TRASH)
            .assertContentDescriptionEquals(detailText(R.string.book_detail_rate_delete))
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)

        rule.runOnIdle { model.value = MyReviewModel.Known(null) }
        rule.onNodeWithTag(MyReviewTestTags.TRASH).assertDoesNotExist()
    }

    // TC-71 (R15)
    @Test
    fun tc71_deleteAsksFirstAndOnlyAConfirmedDeleteCallsBack() {
        var deleted = 0
        show(MyReviewModel.Known(review(rating = 4f, text = "Body")), onDelete = { deleted++ })
        val title = detailText(R.string.book_detail_rate_delete_title)

        rule.onNodeWithTag(MyReviewTestTags.TRASH).performClick()
        rule.onNodeWithText(title).assertExists()
        rule.onNodeWithText(detailText(R.string.book_detail_rate_delete_message)).assertExists()
        rule.onNodeWithTag(MyReviewTestTags.DELETE_CONFIRM)
            .assertTextEquals(detailText(R.string.book_detail_rate_delete_confirm))
        rule.onNodeWithTag(MyReviewTestTags.DELETE_CANCEL)
            .assertTextEquals(detailText(R.string.book_detail_rate_delete_cancel))

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
    fun tc73_theUnknownStatesDisableEverythingAndSayWhatToDo() {
        var rated = 0
        val model = walk(
            MyReviewModel.Unknown(UnknownReason.Loading),
            onRate = { rated++ },
            onCompose = {},
        )
        val cases = listOf(
            UnknownReason.Loading to detailText(R.string.book_detail_rate_note_loading),
            UnknownReason.Offline to detailText(R.string.book_detail_rate_note_offline),
            UnknownReason.Failed to detailText(R.string.book_detail_rate_note_failed),
        )

        cases.forEach { (reason, note) ->
            rule.runOnIdle { model.value = MyReviewModel.Unknown(reason) }
            rule.onNodeWithTag(MyReviewTestTags.NOTE).assertTextEquals(note)
            rule.onNodeWithTag(StarRatingInputTestTags.ROW).assertIsNotEnabled()
            rule.onNodeWithTag(MyReviewTestTags.PENCIL).assertIsNotEnabled()
            rule.onNodeWithTag(MyReviewTestTags.TRASH).assertDoesNotExist()
            rule.onNodeWithTag(MyReviewTestTags.TEXT).assertDoesNotExist()
            rule.onNodeWithTag(MyReviewTestTags.NOTES_BOX).assertDoesNotExist()
            rule.onNodeWithTag(MyReviewTestTags.PROMPT).assertDoesNotExist()

            rule.onNodeWithTag(StarRatingInputTestTags.ROW).performTouchInput { click(Offset(width * 0.5f, height / 2f)) }
            assertFalse(rule.onNodeWithTag(StarRatingInputTestTags.ROW).fetchSemanticsNode().config.contains(SemanticsActions.SetProgress))
        }
        assertEquals(0, rated)
    }

    // TC-74 (R16, S5)
    @Test
    fun tc74_theLoadingStateBreathesOnlyWhileMotionIsAllowed() {
        rule.mainClock.autoAdvance = false
        val model = walkBreathing(MyReviewModel.Unknown(UnknownReason.Loading), reducedMotion = false)

        assertBreathes(true)
        listOf(
            MyReviewModel.Unknown(UnknownReason.Offline),
            MyReviewModel.Unknown(UnknownReason.Failed),
            MyReviewModel.Known(null),
        ).forEach { state ->
            rule.runOnIdle { model.value = state }
            assertBreathes(false)
        }
    }

    // TC-74 (R16, S5)
    @Test
    fun tc74_reducedMotionStopsTheLoadingBreath() {
        rule.mainClock.autoAdvance = false
        walkBreathing(MyReviewModel.Unknown(UnknownReason.Loading), reducedMotion = true)

        assertBreathes(false)
    }

    /** Shows the card for the breathing gate and returns the holder that changes its model. */
    private fun walkBreathing(initial: MyReviewModel, reducedMotion: Boolean): MutableState<MyReviewModel> {
        val model = mutableStateOf(initial)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) {
                    MyReviewCard(model.value, Color.Red, {}, {}, null, reducedMotion = reducedMotion)
                }
            }
        }
        return model
    }

    /** Asserts whether the rating block is marked as breathing. */
    private fun assertBreathes(expected: Boolean) {
        // The clock is paused (the breath never settles), so a state change needs a frame to recompose.
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertEquals(
            expected,
            rule.onNodeWithTag(MyReviewTestTags.RATING_BLOCK).fetchSemanticsNode().config[MyReviewBreathesKey],
        )
    }

    // TC-75 (R11, R15, R20, AC1, AC3, AC4)
    @Test
    fun tc75_aTapSavesARollbackRestoresAndADeleteClearsTheCard() {
        val rates = mutableListOf<Float?>()
        val model = mutableStateOf<MyReviewModel>(MyReviewModel.Known(review(rating = 4f, text = "Body", notes = "private")))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) {
                    MyReviewCard(
                        model = model.value,
                        tint = Color.Red,
                        onRate = { rate ->
                            rates += rate
                            val current = (model.value as MyReviewModel.Known).review!!
                            model.value = MyReviewModel.Known(current.copy(rating = rate))
                        },
                        onDelete = { model.value = MyReviewModel.Known(null) },
                        onCompose = null,
                        reducedMotion = true,
                    )
                }
            }
        }

        rule.onNodeWithTag(StarRatingInputTestTags.ROW).performTouchInput {
            click(Offset(width * 0.95f, height / 2f))
        }
        assertEquals(listOf(5f), rates)
        assertStateDescription(detailText(R.string.book_detail_rate_stars_state, "5"))

        rule.runOnIdle { model.value = MyReviewModel.Known(review(rating = 4f, text = "Body", notes = "private")) }
        assertStateDescription(detailText(R.string.book_detail_rate_stars_state, "4"))

        rule.onNodeWithTag(MyReviewTestTags.TRASH).performClick()
        rule.onNodeWithTag(MyReviewTestTags.DELETE_CONFIRM).performClick()
        assertStateDescription(detailText(R.string.book_detail_rate_stars_unrated))
        rule.onNodeWithTag(MyReviewTestTags.NOTE)
            .assertTextEquals(detailText(R.string.book_detail_rate_note_unrated))
        rule.onNodeWithTag(MyReviewTestTags.TEXT).assertDoesNotExist()
        rule.onNodeWithTag(MyReviewTestTags.NOTES_BOX).assertDoesNotExist()
        rule.onNodeWithTag(MyReviewTestTags.TRASH).assertDoesNotExist()
    }
}
