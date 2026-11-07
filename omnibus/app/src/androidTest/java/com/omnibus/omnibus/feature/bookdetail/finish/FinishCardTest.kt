package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The card's ground for every test: a dark-theme ground for a book of a fixed tone. */
private val testGround = finishGround(Oklch(0.62, 0.12, 60.0), lightGround = false)

/** The totals of the ticket's sample: 17 days, 13 sessions, 4 highlights. */
private val sampleTotals = listOf(FinishTotal.Days(17), FinishTotal.Sessions(13), FinishTotal.Highlights(4))

/** The Sherlock Holmes member the next row points at. */
private val nextMember = SeriesMember("uuid-3", "The Sign of the Four", "3", null, null, false)

/** Clicks and semantics. FinishCardScreenshotTest covers how each card looks. */
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
    fun issue97_tc32_leaveAReviewOpensTheReviewSheet() {
        var reviews = 0
        setCard(
            model = FinishCardModel("The Hound", sampleTotals, FinishAction.Review),
            onLeaveReview = { reviews++ },
        )

        rule.onNodeWithTag(FinishTestTags.REVIEW_ROW).performClick()

        rule.runOnIdle { assertEquals(1, reviews) }
    }

    @Test
    fun issue97_tc33_upNextOpensTheNextBook() {
        val recorder = SectionContextRecorder()
        val context = testSectionContext(recorder = recorder)
        setCard(
            model = FinishCardModel("The Hound", sampleTotals, FinishAction.UpNext(nextMember, "Sherlock Holmes")),
            onOpenBook = { context.onNavigate(BookDetailDestination.Book(it)) },
        )

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
    }
}
