package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The household list: the heading, spoilers, Read more and retry. HouseholdReviewsScreenshotTest covers how it looks. */
class HouseholdReviewsTest {

    @get:Rule
    val rule = createComposeRule()

    private val now = 1_762_000_000_000L

    private fun entry(
        id: Long,
        name: String,
        markdown: String? = null,
        body: ReviewBody = ReviewBody.None,
        veiled: Boolean = false,
    ) = ReviewEntry(
        userId = id,
        name = name,
        hasAvatar = false,
        dateMillis = null,
        rating = null,
        markdown = markdown,
        body = body,
        veiled = veiled,
    )

    private fun listed(vararg entries: ReviewEntry) =
        ReviewsTabModel.Listed(count = entries.size, average = null, entries = entries.toList(), finished = false)

    /** Shows [model] in one composition. */
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

    // TC-48 (AC3)
    @Test
    fun tc48_theListHeadingIsAHeading() {
        show(mutableStateOf(listed(entry(1, "Ada"))))

        rule.onNodeWithText(detailText(R.string.book_detail_reviews_household)).assertIsHeading()
    }

    // TC-52 (AC3)
    @Test
    fun tc52_readMoreExpandsInPlaceOneWay() {
        show(mutableStateOf(listed(entry(1, "Maren", markdown = longText, body = ReviewBody.Text))))

        rule.onNodeWithTag(ReviewsTestTags.readMore(1)).performClick()
        rule.waitForIdle()

        assertTrue(rule.onNodeWithTag(ReviewsTestTags.body(1)).fetchSemanticsNode().size.height > with(rule.density) { 116.dp.toPx() })
        rule.onAllNodesWithTag(ReviewsTestTags.readMore(1)).assertCountEquals(0)
    }

    // TC-53 (AC4)
    @Test
    fun tc53_veiledEntryHidesText() {
        show(mutableStateOf(listed(entry(7, "Priya", markdown = "The butler did it.", body = ReviewBody.Text, veiled = true))))

        rule.onAllNodesWithText("The butler did it.").assertCountEquals(0)
    }

    // TC-54 (AC4)
    @Test
    fun tc54_showReviewCallsOnReveal() {
        val revealed = mutableListOf<Long>()
        show(
            mutableStateOf(listed(entry(7, "Priya", markdown = "The butler did it.", body = ReviewBody.Text, veiled = true))),
            onReveal = { revealed += it },
        )

        rule.onNodeWithTag(ReviewsTestTags.showReview(7)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(7L), revealed)
    }

    // TC-56 (AC6)
    @Test
    fun tc56_failedTabOffersTryAgain() {
        var retries = 0
        show(mutableStateOf(ReviewsTabModel.Failed), onRetry = { retries++ })

        rule.onNodeWithText(detailText(R.string.book_detail_reviews_retry)).performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }
}
