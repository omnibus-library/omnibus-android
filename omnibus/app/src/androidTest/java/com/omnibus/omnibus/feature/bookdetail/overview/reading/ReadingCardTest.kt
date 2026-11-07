package com.omnibus.omnibus.feature.bookdetail.overview.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Your reading card: its TalkBack text. ReadingCardScreenshotTest covers its look. */
class ReadingCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val now = 1_762_000_000_000L

    private val reading = ReadingCardModel(
        line = ReadingLine.ReadingSince(now),
        rows = listOf(ReadingRow.Pace(20)),
    )

    private fun show(card: ReadingCardModel) = rule.setContent {
        OmnibusTheme(darkTheme = true) {
            Column(Modifier.fillMaxWidth()) { ReadingCard(card, now) }
        }
    }

    // TC-43 (R1): the heading is a heading. How the card looks is covered by ReadingCardScreenshotTest.
    @Test
    fun tc43_titleIsAHeading() {
        show(reading)

        rule.onNodeWithTag(ReadingCardTestTags.TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // TC-45 (R18): a row is one node whose description reads label, then value
    @Test
    fun tc45_aRowIsOneNodeReadingLabelThenValue() {
        show(reading)

        val value = detailText(R.string.book_detail_reading_pace_value, 20)
        val node = rule.onNodeWithTag(ReadingCardTestTags.row(ReadingRowKind.Pace)).fetchSemanticsNode()
        assertEquals(
            listOf("${detailText(R.string.book_detail_reading_row_pace)}, $value"),
            node.config[SemanticsProperties.ContentDescription],
        )
        // The merged tree, which TalkBack reads, has no separate value text. The unmerged tree still holds the cleared
        // children, so it is not asserted.
        rule.onAllNodesWithText(value).assertCountEquals(0)
    }
}
