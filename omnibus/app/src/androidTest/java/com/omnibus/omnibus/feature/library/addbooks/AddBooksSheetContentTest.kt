package com.omnibus.omnibus.feature.library.addbooks

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddBooksSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent(
        options: List<AddBooksOption>,
        onSelect: (AddBooksOptionKind) -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddBooksSheetContent(options, onSelect, onDone)
            }
        }
    }

    private fun allEnabled() = listOf(
        AddBooksOption(AddBooksOptionKind.Upload, enabled = true),
        AddBooksOption(AddBooksOptionKind.Scan, enabled = true),
        AddBooksOption(AddBooksOptionKind.Wishlist, enabled = true),
    )

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    // TC-10
    @Test
    fun issue319_tc10_titleIsAHeading() {
        setContent(allEnabled())

        rule.onNodeWithTag(AddBooksTestTags.TITLE)
            .assertTextEquals(text(R.string.add_books_title))
            .assert(isHeading())
    }

    // TC-11
    @Test
    fun issue319_tc11_listsUploadThenScanThenWishlist() {
        setContent(allEnabled())

        val upload = rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Upload)).getUnclippedBoundsInRoot().top
        val scan = rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Scan)).getUnclippedBoundsInRoot().top
        val wishlist = rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Wishlist)).getUnclippedBoundsInRoot().top

        assertTrue("Upload should sit above Scan, was $upload and $scan", upload < scan)
        assertTrue("Scan should sit above Wishlist, was $scan and $wishlist", scan < wishlist)
    }

    // TC-12
    @Test
    fun issue319_tc12_eachRowIsOneButtonReadAsTitleThenDescription() {
        setContent(allEnabled())
        val rows = listOf(
            Triple(AddBooksOptionKind.Upload, R.string.add_books_upload_title, R.string.add_books_upload_description),
            Triple(AddBooksOptionKind.Scan, R.string.add_books_scan_title, R.string.add_books_scan_description),
            Triple(AddBooksOptionKind.Wishlist, R.string.add_books_wishlist_title, R.string.add_books_wishlist_description),
        )

        rows.forEach { (kind, title, description) ->
            rule.onNodeWithTag(AddBooksTestTags.option(kind))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertTextEquals(text(title), text(description))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }
    }

    // TC-13
    @Test
    fun issue319_tc13_offlineDisablesOnlyUpload() {
        setContent(
            listOf(
                AddBooksOption(AddBooksOptionKind.Upload, enabled = false),
                AddBooksOption(AddBooksOptionKind.Scan, enabled = true),
                AddBooksOption(AddBooksOptionKind.Wishlist, enabled = true),
            ),
        )

        rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Upload)).assertIsNotEnabled()
        rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Scan)).assertIsEnabled()
        rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Wishlist)).assertIsEnabled()
    }

    // TC-14
    @Test
    fun issue319_tc14_offlineUploadShowsTheOfflineDescription() {
        setContent(
            listOf(
                AddBooksOption(AddBooksOptionKind.Upload, enabled = false),
                AddBooksOption(AddBooksOptionKind.Scan, enabled = true),
                AddBooksOption(AddBooksOptionKind.Wishlist, enabled = true),
            ),
        )

        rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Upload))
            .assertTextEquals(text(R.string.add_books_upload_title), text(R.string.add_books_upload_offline))
    }

    // TC-15
    @Test
    fun issue319_tc15_tappingARowReportsItsKind() {
        val selected = mutableListOf<AddBooksOptionKind>()
        setContent(allEnabled(), onSelect = { selected += it })

        rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Upload)).performClick()
        rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Scan)).performClick()
        rule.onNodeWithTag(AddBooksTestTags.option(AddBooksOptionKind.Wishlist)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(AddBooksOptionKind.Upload, AddBooksOptionKind.Scan, AddBooksOptionKind.Wishlist), selected)
    }

    // TC-16
    @Test
    fun issue319_tc16_rowsAreAtLeast48dpTall() {
        setContent(allEnabled())

        AddBooksOptionKind.entries.forEach { kind ->
            rule.onNodeWithTag(AddBooksTestTags.option(kind)).assertHeightIsAtLeast(48.dp)
        }
    }
}
