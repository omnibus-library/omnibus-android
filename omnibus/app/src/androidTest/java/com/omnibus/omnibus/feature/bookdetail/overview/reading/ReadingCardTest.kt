package com.omnibus.omnibus.feature.bookdetail.overview.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** The Your reading card: heading and line, the rows in order, their TalkBack text, and the Hilt-free gate branches. */
class ReadingCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val zone: TimeZone = TimeZone.getDefault()
    private val locale: Locale
        get() = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]

    private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        GregorianCalendar(zone).apply { clear(); set(2026, month - 1, day, hour, minute) }.timeInMillis

    private val now = at(11, 2)
    private val oct12 = at(10, 12, 20)
    private val oct19 = at(10, 19, 18)
    private val oct29 = at(10, 29, 22, 14)
    private val nov1At2214 = at(11, 1, 22, 14)

    private val reading = ReadingCardModel(
        line = ReadingLine.ReadingSince(oct12),
        rows = listOf(
            ReadingRow.Started(oct12),
            ReadingRow.TimeRead(11_040L, 11),
            ReadingRow.Pace(20),
            ReadingRow.LastRead(nov1At2214),
            ReadingRow.LongestSitting(2_880L, oct19),
            ReadingRow.TimeLeft(6_780L),
        ),
    )

    private fun show(card: ReadingCardModel) = rule.setContent {
        OmnibusTheme(darkTheme = true) {
            Column(Modifier.fillMaxWidth()) { ReadingCard(card, now) }
        }
    }

    private fun longDate(millis: Long): String = BookDetailFormat.longDate(millis, now, locale, zone)

    private fun sessions(count: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources
            .getQuantityString(R.plurals.book_detail_reading_sessions, count, count)

    // TC-43 (R1, R18): the heading is a heading, the line sits under it, and the card fills the width
    @Test
    fun tc43_headingLineAndFullWidth() {
        show(reading)

        rule.onNodeWithTag(ReadingCardTestTags.TITLE).assertTextEquals(detailText(R.string.book_detail_reading_title))
        rule.onNodeWithTag(ReadingCardTestTags.TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag(ReadingCardTestTags.LINE)
            .assertTextEquals(detailText(R.string.book_detail_reading_line_reading_since, longDate(oct12)))

        val title = rule.onNodeWithTag(ReadingCardTestTags.TITLE).bounds()
        val line = rule.onNodeWithTag(ReadingCardTestTags.LINE).bounds()
        assertTrue(line.top >= title.bottom)

        val card = rule.onNodeWithTag(ReadingCardTestTags.CARD).bounds()
        val root = rule.onRoot().bounds()
        assertEquals(root.width, card.width, 1f)
    }

    // TC-45 (R18): each row is one node whose description reads label, then value
    @Test
    fun tc45_eachRowIsOneNodeReadingLabelThenValue() {
        show(reading)

        val expected = mapOf(
            ReadingRowKind.Started to "${detailText(R.string.book_detail_reading_row_started)}, ${longDate(oct12)}",
            ReadingRowKind.TimeRead to "${detailText(R.string.book_detail_reading_row_time_read)}, " +
                detailText(R.string.book_detail_reading_time_read_value, BookDetailFormat.hoursMinutes(11_040L), sessions(11)),
            ReadingRowKind.Pace to "${detailText(R.string.book_detail_reading_row_pace)}, " +
                detailText(R.string.book_detail_reading_pace_value, 20),
            ReadingRowKind.LastRead to "${detailText(R.string.book_detail_reading_row_last_read)}, " +
                BookDetailFormat.relativeDateTime(
                    nov1At2214,
                    now,
                    locale,
                    zone,
                    detailText(R.string.book_detail_reading_today),
                    detailText(R.string.book_detail_reading_yesterday),
                ),
            ReadingRowKind.LongestSitting to "${detailText(R.string.book_detail_reading_row_longest_sitting)}, " +
                "${BookDetailFormat.hoursMinutes(2_880L)}, ${longDate(oct19)}",
            ReadingRowKind.TimeLeft to "${detailText(R.string.book_detail_reading_row_time_left)}, " +
                detailText(R.string.book_detail_reading_time_left_value, BookDetailFormat.hoursMinutes(6_780L)),
        )

        for ((kind, description) in expected) {
            val node = rule.onNodeWithTag(ReadingCardTestTags.row(kind)).fetchSemanticsNode()
            assertEquals(listOf(description), node.config[SemanticsProperties.ContentDescription])
        }
        // The merged tree, which TalkBack reads, has no separate value text. The unmerged tree still holds the cleared
        // children, so it is not asserted.
        rule.onAllNodesWithText(detailText(R.string.book_detail_reading_pace_value, 20)).assertCountEquals(0)
    }

    // TC-46 (R3): a hold shows the heading alone
    @Test
    fun tc46_holdShowsOnlyTheHeading() {
        show(ReadingCardModel(line = null, rows = emptyList()))

        rule.onNodeWithTag(ReadingCardTestTags.TITLE).assertIsDisplayed()
        rule.onAllNodesWithTag(ReadingCardTestTags.LINE).assertCountEquals(0)
        for (kind in ReadingRowKind.entries) {
            rule.onAllNodesWithTag(ReadingCardTestTags.row(kind)).assertCountEquals(0)
        }
    }

    // TC-47 (R4, R5, R6, R7, R9, R13, R17): the line variants, and the rows left out for each
    @Test
    fun tc47_lineVariantsAndLeftOutRows() {
        // One composition for all three variants: the rule allows a single setContent.
        val card = mutableStateOf(ReadingCardModel(ReadingLine.NoReading, emptyList()))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxWidth()) { ReadingCard(card.value, now) }
            }
        }

        rule.onNodeWithTag(ReadingCardTestTags.LINE)
            .assertTextEquals(detailText(R.string.book_detail_reading_line_no_reading))
        for (kind in ReadingRowKind.entries) {
            rule.onAllNodesWithTag(ReadingCardTestTags.row(kind)).assertCountEquals(0)
        }

        rule.runOnUiThread { card.value = ReadingCardModel(ReadingLine.HistoryFailed, emptyList()) }
        rule.waitForIdle()
        rule.onNodeWithTag(ReadingCardTestTags.LINE)
            .assertTextEquals(detailText(R.string.book_detail_reading_line_history_failed))
        for (kind in ReadingRowKind.entries) {
            rule.onAllNodesWithTag(ReadingCardTestTags.row(kind)).assertCountEquals(0)
        }

        rule.runOnUiThread {
            card.value = ReadingCardModel(
                line = ReadingLine.Finished(oct29),
                rows = listOf(
                    ReadingRow.Started(oct12),
                    ReadingRow.TimeRead(11_040L, 11),
                    ReadingRow.LastRead(nov1At2214),
                    ReadingRow.LongestSitting(2_880L, oct19),
                ),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ReadingCardTestTags.LINE)
            .assertTextEquals(detailText(R.string.book_detail_reading_line_finished, longDate(oct29)))
        rule.onAllNodesWithTag(ReadingCardTestTags.row(ReadingRowKind.TimeLeft)).assertCountEquals(0)
        rule.onAllNodesWithTag(ReadingCardTestTags.row(ReadingRowKind.Pace)).assertCountEquals(0)
        for (kind in listOf(ReadingRowKind.Started, ReadingRowKind.TimeRead, ReadingRowKind.LastRead, ReadingRowKind.LongestSitting)) {
            rule.onAllNodesWithTag(ReadingCardTestTags.row(kind)).assertCountEquals(1)
        }
    }

    // TC-48 (R2, R10): a wishlist-only book shows the static card, with no rows
    @Test
    fun tc48_wishlistOnlyShowsTheStaticCard() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadingCardSection.Content(
                    context = testSectionContext(book = testDetailBook().copy(isWishlisted = true)),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(ReadingCardTestTags.TITLE).assertIsDisplayed()
        rule.onNodeWithTag(ReadingCardTestTags.LINE)
            .assertTextEquals(detailText(R.string.book_detail_reading_line_wishlist))
        for (kind in ReadingRowKind.entries) {
            rule.onAllNodesWithTag(ReadingCardTestTags.row(kind)).assertCountEquals(0)
        }
    }

    // TC-49 (R2): a book with no copy renders nothing, without touching Hilt
    @Test
    fun tc49_noCopyRendersNothing() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadingCardSection.Content(
                    context = testSectionContext(book = testDetailBook(formats = emptyList(), creators = emptyList())),
                    modifier = Modifier,
                )
            }
        }

        rule.onAllNodesWithTag(ReadingCardTestTags.CARD).assertCountEquals(0)
        rule.onAllNodesWithTag(ReadingCardTestTags.TITLE).assertCountEquals(0)
    }
}
