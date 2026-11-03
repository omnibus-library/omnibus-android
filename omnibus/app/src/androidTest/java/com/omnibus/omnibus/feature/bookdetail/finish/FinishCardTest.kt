package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The card's ground for every test: a dark-theme ground for a book of a fixed tone. */
private val testGround = finishGround(Oklch(0.62, 0.12, 60.0), lightGround = false)

/** The totals of the ticket's sample: 17 days, 13 sessions, 4 highlights. */
private val sampleTotals = listOf(FinishTotal.Days(17), FinishTotal.Sessions(13), FinishTotal.Highlights(4))

/** The Sherlock Holmes member the next row points at. */
private val nextMember = SeriesMember("uuid-3", "The Sign of the Four", "3", null, null, false)

class FinishCardTest {

    @get:Rule
    val rule = createComposeRule()

    /** Renders [model] in a full-size column, in dark theme. */
    private fun setCard(
        model: FinishCardModel,
        onLeaveReview: () -> Unit = {},
        onOpenBook: (String) -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize()) {
                    FinishCard(
                        model = model,
                        ground = testGround,
                        onLeaveReview = onLeaveReview,
                        onOpenBook = onOpenBook,
                    )
                }
            }
        }
    }

    @Test
    fun issue97_tc29_anUnratedFinishedBookShowsTotalsAndLeaveAReview() {
        setCard(FinishCardModel("The Hound", sampleTotals, FinishAction.Review))

        rule.onNodeWithText(detailText(R.string.book_detail_finish_title, "The Hound")).assertIsDisplayed()
        val totals = rule.onNodeWithTag(FinishTestTags.TOTALS)
        totals.assertIsDisplayed()
        val expected = sampleTotals.totalsText(finishWording(InstrumentationRegistry.getInstrumentation().targetContext.resources))
        assertEquals(expected, totals.fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
        rule.onNodeWithText(detailText(R.string.book_detail_finish_review_title)).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_finish_review_detail)).assertIsDisplayed()
    }

    @Test
    fun issue97_tc30_noFinishDateShowsTheCardWithoutTotals() {
        setCard(FinishCardModel("The Hound", emptyList(), FinishAction.Review))

        rule.onNodeWithTag(FinishTestTags.TITLE).assertIsDisplayed()
        rule.onNodeWithTag(FinishTestTags.TOTALS).assertDoesNotExist()
        rule.onNodeWithTag(FinishTestTags.REVIEW_ROW).assertIsDisplayed()
    }

    @Test
    fun issue97_tc31_anUnknownReviewShowsNoActionRow() {
        setCard(FinishCardModel("The Hound", sampleTotals, null))

        rule.onNodeWithTag(FinishTestTags.CARD).assertIsDisplayed()
        rule.onNodeWithTag(FinishTestTags.REVIEW_ROW).assertDoesNotExist()
        rule.onNodeWithTag(FinishTestTags.NEXT_ROW).assertDoesNotExist()
    }

    @Test
    fun issue97_tc32_leaveAReviewOpensTheReviewSheet() {
        val recorder = SectionContextRecorder()
        var reviews = 0
        setCard(
            model = FinishCardModel("The Hound", sampleTotals, FinishAction.Review),
            onLeaveReview = { reviews++ },
            onOpenBook = { recorder.navigations += BookDetailDestination.Book(it) },
        )

        rule.onNodeWithTag(FinishTestTags.REVIEW_ROW).performClick()

        rule.runOnIdle {
            assertEquals(1, reviews)
            assertTrue(recorder.navigations.isEmpty())
        }
    }

    @Test
    fun issue97_tc33_upNextShowsTheNextBookAndOpensIt() {
        val recorder = SectionContextRecorder()
        val context = testSectionContext(recorder = recorder)
        setCard(
            model = FinishCardModel("The Hound", sampleTotals, FinishAction.UpNext(nextMember, "Sherlock Holmes")),
            onOpenBook = { context.onNavigate(BookDetailDestination.Book(it)) },
        )

        rule.onNodeWithText(detailText(R.string.book_detail_finish_next_title, "The Sign of the Four")).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_finish_next_detail, "3", "Sherlock Holmes")).assertIsDisplayed()

        val titleBottom = rule.onNodeWithTag(FinishTestTags.TITLE).bounds().bottom
        val rowTop = rule.onNodeWithTag(FinishTestTags.NEXT_ROW).bounds().top
        assertTrue("row top $rowTop is above the title bottom $titleBottom", rowTop >= titleBottom)

        rule.onNodeWithTag(FinishTestTags.NEXT_ROW).performClick()

        rule.runOnIdle {
            assertEquals(listOf<BookDetailDestination>(BookDetailDestination.Book("uuid-3")), recorder.navigations)
        }
    }

    @Test
    fun issue97_tc34_theCardIsAHeadingWithReadableButtonRows() {
        setCard(FinishCardModel("The Hound", sampleTotals, FinishAction.Review))

        rule.onNodeWithTag(FinishTestTags.TITLE, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        val review = rule.onNodeWithTag(FinishTestTags.REVIEW_ROW)
        review.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(
            detailText(R.string.book_detail_finish_review_action),
            review.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        val reviewText = review.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(reviewText.contains(detailText(R.string.book_detail_finish_review_title)))
        assertTrue(reviewText.contains(detailText(R.string.book_detail_finish_review_detail)))
    }

    @Test
    fun issue97_tc34_theNextRowIsAButtonWithItsOwnLabel() {
        setCard(FinishCardModel("The Hound", sampleTotals, FinishAction.UpNext(nextMember, "Sherlock Holmes")))

        val next = rule.onNodeWithTag(FinishTestTags.NEXT_ROW)
        next.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(
            detailText(R.string.book_detail_finish_next_action),
            next.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        val nextText = next.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(nextText.contains("The Sign of the Four"))
        assertTrue(nextText.contains(detailText(R.string.book_detail_finish_next_detail, "3", "Sherlock Holmes")))
    }
}
