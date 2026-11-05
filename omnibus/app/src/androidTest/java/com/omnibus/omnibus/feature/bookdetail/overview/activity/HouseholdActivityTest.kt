package com.omnibus.omnibus.feature.bookdetail.overview.activity

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Activity list: rows, wording, the initial fallback and the one-item-per-reader semantics. */
class HouseholdActivityTest {

    @get:Rule
    val rule = createComposeRule()

    private val maren = HouseholdReader(3, "Maren", hasAvatar = false, seconds = 22_320, sessions = 4, lastSessionAtMillis = 1_761_000_000_000L)
    private val theo = HouseholdReader(4, "Theo", hasAvatar = false, seconds = 7_260, sessions = 1, lastSessionAtMillis = 1_761_775_445_000L)

    private fun show(readers: List<HouseholdReader>) = rule.setContent {
        OmnibusTheme(darkTheme = true) { HouseholdActivity(readers, Modifier) }
    }

    // TC-30 (AC1)
    @Test
    fun tc30_listsEachReaderWithTheirLine() {
        show(listOf(maren, theo))

        rule.onNodeWithTag(HouseholdActivityTestTags.TITLE).assertTextEquals(detailText(R.string.book_detail_activity_title))
        rule.onNodeWithText("Maren").assertIsDisplayed()
        rule.onNodeWithText("Theo").assertIsDisplayed()
        rule.onNodeWithTag(HouseholdActivityTestTags.row(3)).assertTextContains("6 h 12 m · 4 sessions", substring = true)
        rule.onNodeWithTag(HouseholdActivityTestTags.row(3)).assertTextContains("Last read", substring = true)
        rule.onNodeWithTag(HouseholdActivityTestTags.row(4)).assertTextContains("2 h 01 m · 1 session", substring = true)
    }

    // TC-31 (AC2)
    @Test
    fun tc31_noReadersShowsNoSection() {
        show(emptyList())

        rule.onAllNodesWithTag(HouseholdActivityTestTags.SECTION).assertCountEquals(0)
        rule.onAllNodesWithTag(HouseholdActivityTestTags.TITLE).assertCountEquals(0)
    }

    // TC-32 (AC3)
    @Test
    fun tc32_readerWithoutPictureShowsTheirInitial() {
        show(listOf(maren, theo.copy(name = "theo")))

        rule.onNodeWithTag(HouseholdActivityTestTags.row(3), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("M", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("T", useUnmergedTree = true).assertIsDisplayed()
    }

    // TC-33 (AC4)
    @Test
    fun tc33_eachRowReadsAsOneItemNameThenStatus() {
        show(listOf(maren, theo))

        val row = rule.onNodeWithTag(HouseholdActivityTestTags.row(3)).fetchSemanticsNode()
        val texts = row.config[SemanticsProperties.Text].map { it.text }
        assertEquals(2, texts.size)
        assertEquals("Maren", texts[0])
        assertEquals(true, texts[1].startsWith("Last read") && texts[1].endsWith("6 h 12 m · 4 sessions"))
        // One row, one node: the avatar contributes no node of its own to the merged tree.
        rule.onAllNodesWithTag(HouseholdActivityTestTags.row(3)).assertCountEquals(1)
    }

    // TC-35: a book with nothing to read renders nothing without creating a ViewModel
    @Test
    fun tc35_sectionRendersNothingForABookWithNoCopy() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                HouseholdActivitySection.Content(
                    context = testSectionContext(book = testDetailBook(formats = emptyList())),
                    modifier = Modifier,
                )
            }
        }

        rule.onAllNodesWithTag(HouseholdActivityTestTags.SECTION).assertCountEquals(0)
    }
}
