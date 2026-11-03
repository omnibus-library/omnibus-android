package com.omnibus.omnibus.feature.bookdetail.files.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** The Details list at the end of the Files tab: which rows show, how values are cut, copied and read out. */
class BookDetailsTest {

    @get:Rule
    val rule = createComposeRule()

    private val tags = BookDetailsTestTags

    private val publisher = DetailItem(DetailRow.Publisher, "Spencer Blackett")
    private val file = DetailItem(DetailRow.File, "hound.epub")

    private fun show(items: List<DetailItem>) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) { BookDetails(items, Modifier.fillMaxWidth()) }
        }
        rule.waitForIdle()
    }

    // TC-1 (AC1)
    @Test
    fun tc1_showsTheHeadingAndOneLabelledRowPerItem() {
        show(listOf(publisher, DetailItem(DetailRow.Language, "English"), file))

        rule.onNodeWithTag(tags.TITLE).assertTextEquals(detailText(R.string.book_detail_details_title))
        rule.onNodeWithTag(tags.row(DetailRow.Publisher)).assertIsDisplayed()
        rule.onNodeWithTag(tags.row(DetailRow.Language)).assertIsDisplayed()
        rule.onNodeWithTag(tags.row(DetailRow.File)).assertIsDisplayed()
        valueNode(DetailRow.Publisher).assertTextEquals("Spencer Blackett")
        valueNode(DetailRow.Language).assertTextEquals("English")
    }

    // TC-2 (AC1)
    @Test
    fun tc2_showsNoRowForADetailTheBookLacks() {
        show(listOf(file))

        rule.onAllNodesWithTag(tags.row(DetailRow.File)).assertCountEquals(1)
        rule.onAllNodesWithTag(tags.row(DetailRow.Publisher)).assertCountEquals(0)
        rule.onAllNodesWithTag(tags.row(DetailRow.Isbn)).assertCountEquals(0)
    }

    // TC-3 (AC1)
    @Test
    fun tc3_noItemsShowNoHeadingEither() {
        show(emptyList())

        rule.onAllNodesWithTag(tags.SECTION).assertCountEquals(0)
        rule.onAllNodesWithTag(tags.TITLE).assertCountEquals(0)
    }

    // TC-4 (AC1, AC5)
    @Test
    fun tc4_theSectionBuildsItsRowsFromTheBookRecord() {
        val book = testDetailBook(series = "Sherlock Holmes", seriesIndex = "2").copy(
            publisher = "Spencer Blackett",
            isbn13 = "9780141034357",
        )
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailsSection.Content(testSectionContext(book = book), Modifier.fillMaxWidth())
            }
        }
        rule.waitForIdle()

        valueNode(DetailRow.Publisher).assertTextEquals("Spencer Blackett")
        valueNode(DetailRow.Series)
            .assertTextEquals(detailText(R.string.book_detail_details_series_book, "Sherlock Holmes", "2"))
        valueNode(DetailRow.Isbn).assertTextEquals("9780141034357")
        valueNode(DetailRow.File).assertTextEquals("book-1.epub")
        rule.onAllNodesWithTag(tags.row(DetailRow.Published)).assertCountEquals(0)
    }

    // TC-5 (AC4)
    @Test
    fun tc5_eachRowReadsAsLabelCommaValue() {
        show(listOf(publisher, file))

        rule.onNodeWithTag(tags.row(DetailRow.Publisher))
            .assertContentDescriptionEquals(detailText(R.string.book_detail_details_row_description, "Publisher", "Spencer Blackett"))
        rule.onNodeWithTag(tags.row(DetailRow.File))
            .assertContentDescriptionEquals(detailText(R.string.book_detail_details_row_description, "File", "hound.epub"))
        // One row, one node: the label and value merge into it.
        rule.onAllNodesWithTag(tags.row(DetailRow.Publisher)).assertCountEquals(1)
    }

    // TC-6 (AC3)
    @Test
    fun tc6_aShortValueIsShownWholeAndRightAligned() {
        show(listOf(publisher))

        val value = valueNode(DetailRow.Publisher)
        value.assertTextEquals("Spencer Blackett")
        val layout = value.textLayout()
        assertEquals(1, layout.lineCount)
        // The text ends at the row's right edge.
        assertTrue(layout.size.width - layout.getLineRight(0) <= 2f)
    }

    // TC-7 (AC3)
    @Test
    fun tc7_aLongValueIsCutInTheMiddleOnTwoLines() {
        val long = "Start_" + "x".repeat(300) + "_end.epub"
        show(listOf(DetailItem(DetailRow.File, long)))

        val value = valueNode(DetailRow.File)
        val text = value.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        assertTrue(text, text.contains("…"))
        assertTrue(text, text.startsWith("Start_"))
        assertTrue(text, text.endsWith("_end.epub"))
        assertTrue(text.length < long.length)
        val layout = value.textLayout()
        assertEquals(2, layout.lineCount)
        assertTrue(!layout.hasVisualOverflow)
        // TalkBack still gets the whole value.
        rule.onNodeWithTag(tags.row(DetailRow.File))
            .assertContentDescriptionEquals(detailText(R.string.book_detail_details_row_description, "File", long))
    }

    // TC-8 (AC3)
    @Test
    fun tc8_copyingFromACutValueStoresAllOfIt() {
        val clipboard = RecordingClipboard()

        runBlocking {
            WholeValueClipboard(clipboard, "Start_whole_end.epub")
                .setClipEntry(ClipEntry(ClipData.newPlainText(null, "Start_wh…ole_end.epub")))
        }

        assertEquals("Start_whole_end.epub", clipboard.stored?.clipData?.getItemAt(0)?.text?.toString())
    }

    // TC-9 (AC3)
    @Test
    fun tc9_theValuesAreInASelectionContainer() {
        show(listOf(publisher))

        // A long press selects the text rather than doing nothing or crashing, and the value stays on screen.
        valueNode(DetailRow.Publisher).performTouchInput { longClick() }
        rule.waitForIdle()

        valueNode(DetailRow.Publisher).assertIsDisplayed()
    }

    // TC-10
    @Test
    fun tc10_labelsSitLeftAndValuesRightOnTheSameRow() {
        show(listOf(publisher))

        val row = rule.onNodeWithTag(tags.row(DetailRow.Publisher)).bounds()
        val value = valueNode(DetailRow.Publisher).bounds()
        assertTrue(value.left > row.left)
        assertEquals(row.right, value.right, 1f)
    }

    /** The value text, which is merged into its row's node, so it is found in the unmerged tree. */
    private fun valueNode(row: DetailRow) = rule.onNodeWithTag(tags.value(row), useUnmergedTree = true)

    /** A clipboard that keeps what it is given. */
    private class RecordingClipboard : Clipboard {
        var stored: ClipEntry? = null

        override suspend fun getClipEntry(): ClipEntry? = stored

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            stored = clipEntry
        }

        override val nativeClipboard: ClipboardManager
            get() = InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}
