package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Reviews tab's household list: its heading and entries, spoilers, long text, and the tab's notices. */
class HouseholdReviewsTest {

    @get:Rule
    val rule = createComposeRule()

    private val now = 1_762_000_000_000L
    private val locale get() = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]

    private fun entry(
        id: Long,
        name: String,
        date: Long? = null,
        rating: Float? = null,
        markdown: String? = null,
        body: ReviewBody = ReviewBody.None,
        veiled: Boolean = false,
    ) = ReviewEntry(
        userId = id,
        name = name,
        hasAvatar = false,
        dateMillis = date,
        rating = rating,
        markdown = markdown,
        body = body,
        veiled = veiled,
    )

    private fun listed(vararg entries: ReviewEntry, average: Float? = null) =
        ReviewsTabModel.Listed(count = entries.size, average = average, entries = entries.toList(), finished = false)

    /** Shows [model] in one composition, and returns the holder that changes it for a walk through states. */
    private fun show(
        model: MutableState<ReviewsTabModel>,
        onReveal: (Long) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = rule.setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxWidth()) {
                HouseholdReviews(model.value, Color.Red, now, onReveal, onRetry)
            }
        }
    }

    /** Asserts that the node is a heading for accessibility services. */
    private fun SemanticsNodeInteraction.assertIsHeading(): SemanticsNodeInteraction =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))

    /** A long review, taller than the 116 dp clip at any width. */
    private val longText = List(40) { "Sentence $it of a long review that keeps going." }.joinToString(" ")

    // TC-47 (AC2)
    @Test
    fun tc47_emptyShowsTheHeadingAndMessage() {
        show(mutableStateOf(ReviewsTabModel.Empty))

        rule.onNodeWithText(detailText(R.string.book_detail_reviews_empty_title)).assertIsDisplayed().assertIsHeading()
        rule.onNodeWithText(detailText(R.string.book_detail_reviews_empty_message)).assertIsDisplayed()
        rule.onAllNodesWithText(detailText(R.string.book_detail_reviews_household)).assertCountEquals(0)
    }

    // TC-48 (AC3)
    @Test
    fun tc48_listShowsHeadingCountAndAverage() {
        val model = mutableStateOf<ReviewsTabModel>(
            listed(entry(1, "Ada"), entry(2, "Bo"), entry(3, "Cy"), average = 4.3f),
        )
        show(model)

        rule.onNodeWithText(detailText(R.string.book_detail_reviews_household)).assertIsDisplayed().assertIsHeading()
        rule.onNodeWithTag(ReviewsTestTags.COUNT).assertTextEquals("3")
        rule.onNodeWithText(detailText(R.string.book_detail_reviews_average, "4.3")).assertIsDisplayed()

        rule.runOnIdle { model.value = listed(entry(1, "Ada"), entry(2, "Bo"), entry(3, "Cy")) }
        rule.onAllNodesWithText(detailText(R.string.book_detail_reviews_average, "4.3")).assertCountEquals(0)
    }

    // TC-49 (AC3)
    @Test
    fun tc49_entryShowsNameDateStarsAndTextAndOmitsMissingOnes() {
        val date = 1_749_859_200_000L
        show(
            mutableStateOf(
                listed(
                    entry(3, "Maren", date = date, rating = 4.5f, markdown = "Loved it.", body = ReviewBody.Text),
                    entry(4, "Theo", markdown = "Slow start.", body = ReviewBody.Text),
                ),
            ),
        )

        val dated = "Maren · ${BookDetailFormat.longDate(date, now, locale, TimeZone.getDefault())}"
        rule.onNodeWithText(dated).assertIsDisplayed()
        rule.onNodeWithTag(ReviewsTestTags.stars(3))
            .assertContentDescriptionEquals(detailText(R.string.book_detail_reviews_stars_description, "4.5"))
        rule.onAllNodesWithText("4.5").assertCountEquals(0)
        rule.onNodeWithText("Theo").assertIsDisplayed()
        rule.onAllNodesWithTag(ReviewsTestTags.stars(4)).assertCountEquals(0)
        rule.onNodeWithText("Loved it.").assertIsDisplayed()
        rule.onNodeWithText("Slow start.").assertIsDisplayed()
    }

    // TC-50 (AC5)
    @Test
    fun tc50_ratedOnlyAndImageOnlyShowTheirNotes() {
        show(
            mutableStateOf(
                listed(
                    entry(5, "Jonas", rating = 3.5f, body = ReviewBody.RatedOnly),
                    entry(6, "Ada", rating = 4f, markdown = "![x](u)", body = ReviewBody.ImageOnly),
                ),
            ),
        )

        rule.onAllNodesWithText(detailText(R.string.book_detail_reviews_rated_only)).assertCountEquals(1)
        rule.onAllNodesWithText(detailText(R.string.book_detail_reviews_image_only)).assertCountEquals(1)
        rule.onAllNodesWithTag(ReviewsTestTags.readMore(5)).assertCountEquals(0)
        rule.onAllNodesWithTag(ReviewsTestTags.readMore(6)).assertCountEquals(0)
    }

    // TC-52 (AC3)
    @Test
    fun tc52_readMoreExpandsInPlaceOneWay() {
        show(mutableStateOf(listed(entry(1, "Maren", markdown = longText, body = ReviewBody.Text))))

        rule.onNodeWithTag(ReviewsTestTags.readMore(1)).performClick()
        rule.waitForIdle()

        assertTrue(rule.onNodeWithTag(ReviewsTestTags.body(1)).fetchSemanticsNode().size.height > with(rule.density) { 116.dp.toPx() })
        rule.onAllNodesWithTag(ReviewsTestTags.readMore(1)).assertCountEquals(0)
        rule.onAllNodesWithText("Show less").assertCountEquals(0)
    }

    // TC-53 (AC4)
    @Test
    fun tc53_veiledEntryHidesTextAndKeepsStars() {
        show(
            mutableStateOf(
                listed(entry(7, "Priya", rating = 4f, markdown = "The butler did it.", body = ReviewBody.Text, veiled = true)),
            ),
        )

        rule.onAllNodesWithText("The butler did it.").assertCountEquals(0)
        rule.onNodeWithText(detailText(R.string.book_detail_reviews_spoiler_warning)).assertIsDisplayed()
        rule.onNodeWithTag(ReviewsTestTags.showReview(7))
            .assertIsDisplayed()
            .assertTextEquals(detailText(R.string.book_detail_reviews_show_review))
            .assertHeightIsAtLeast(44.dp)
        rule.onNodeWithTag(ReviewsTestTags.stars(7))
            .assertContentDescriptionEquals(detailText(R.string.book_detail_reviews_stars_description, "4"))
    }

    // TC-54 (AC4)
    @Test
    fun tc54_showReviewCallsOnRevealAndOpensTheText() {
        val revealed = mutableListOf<Long>()
        val model = mutableStateOf<ReviewsTabModel>(
            listed(entry(7, "Priya", markdown = "The butler did it.", body = ReviewBody.Text, veiled = true)),
        )
        show(
            model,
            onReveal = { id ->
                revealed += id
                model.value = listed(entry(7, "Priya", markdown = "The butler did it.", body = ReviewBody.Text))
            },
        )

        rule.onNodeWithTag(ReviewsTestTags.showReview(7)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(7L), revealed)
        rule.onNodeWithText("The butler did it.").assertIsDisplayed()
        rule.onAllNodesWithText(detailText(R.string.book_detail_reviews_spoiler_warning)).assertCountEquals(0)
        rule.onAllNodesWithTag(ReviewsTestTags.showReview(7)).assertCountEquals(0)
    }

    // TC-55 (AC6)
    @Test
    fun tc55_offlineShowsTheConnectionLineAndHiddenShowsNothing() {
        val line = detailText(R.string.book_detail_reviews_tab_offline)
        val model = mutableStateOf<ReviewsTabModel>(ReviewsTabModel.Offline)
        show(model)

        rule.onNodeWithText(line).assertIsDisplayed()
        rule.onAllNodesWithText(detailText(R.string.book_detail_reviews_retry)).assertCountEquals(0)

        rule.runOnIdle { model.value = ReviewsTabModel.Hidden }
        rule.onAllNodesWithTag(ReviewsTestTags.SECTION).assertCountEquals(0)
        rule.onAllNodesWithText(line).assertCountEquals(0)
    }

    // TC-56 (AC6)
    @Test
    fun tc56_failedTabOffersTryAgain() {
        var retries = 0
        show(mutableStateOf(ReviewsTabModel.Failed), onRetry = { retries++ })

        rule.onNodeWithText(detailText(R.string.book_detail_reviews_tab_failed)).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_reviews_retry)).assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }
}
