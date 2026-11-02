package com.omnibus.omnibus.feature.bookdetail.overview.author

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfTestTags
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MoreByAuthorSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shelfBook(n: Int, year: String? = "19$n") = AuthorShelfBook(
        uuid = "uuid-$n",
        identity = CoverIdentity(uuid = "uuid-$n", title = "Book $n", hasCover = false),
        title = "Book $n",
        year = year,
    )

    private fun setSection(
        books: List<AuthorShelfBook>,
        onOpenAuthorPage: (() -> Unit)? = {},
        onOpenBook: (String) -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MoreByAuthor("Ursula K. Le Guin", books, onOpenAuthorPage, onOpenBook, Modifier)
            }
        }
    }

    // #105 TC-36 (AC1)
    @Test
    fun tc36_showsTheHeadingNamingTheAuthorAndOneCardPerBook() {
        setSection(books = listOf(shelfBook(1), shelfBook(2), shelfBook(3)))

        rule.onNodeWithTag(MoreByAuthorTestTags.HEADING).assertIsDisplayed()
        val heading = rule.onNodeWithTag(MoreByAuthorTestTags.HEADING).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().single().text
        assertEquals(detailText(R.string.book_detail_author_heading, "Ursula K. Le Guin"), heading)
        rule.onAllNodesWithTag(BookShelfTestTags.CARD).assertCountEquals(3)
    }

    // #105 TC-37 (AC2)
    @Test
    fun tc37_tappingACardOpensThatBook() {
        val opened = mutableListOf<String>()
        setSection(books = listOf(shelfBook(1), shelfBook(2)), onOpenBook = { opened += it })

        rule.onAllNodesWithTag(BookShelfTestTags.CARD)[1].performClick()

        assertEquals(listOf("uuid-2"), opened)
    }

    // #105 TC-38 (AC3)
    @Test
    fun tc38_theAuthorPageLinkOpensTheAuthorPage() {
        var opened = 0
        setSection(books = listOf(shelfBook(1)), onOpenAuthorPage = { opened++ })

        rule.onNodeWithTag(MoreByAuthorTestTags.LINK).performClick()

        assertEquals(1, opened)
    }

    // #105 TC-39 (AC3)
    @Test
    fun tc39_withoutAnAuthorPageThereIsNoLink() {
        setSection(books = listOf(shelfBook(1)), onOpenAuthorPage = null)

        rule.onNodeWithTag(MoreByAuthorTestTags.LINK).assertDoesNotExist()
        rule.onNodeWithTag(MoreByAuthorTestTags.HEADING).assertIsDisplayed()
    }

    // #105 TC-40 (AC4)
    @Test
    fun tc40_noBooksShowNoSection() {
        setSection(books = emptyList())

        rule.onNodeWithTag(MoreByAuthorTestTags.SECTION).assertDoesNotExist()
        rule.onNodeWithTag(MoreByAuthorTestTags.HEADING).assertDoesNotExist()
        rule.onNodeWithTag(MoreByAuthorTestTags.LINK).assertDoesNotExist()
    }

    // #105 TC-41 (AC5)
    @Test
    fun tc41_eachBookIsAnnouncedWithItsTitleAndYear() {
        setSection(books = listOf(shelfBook(1, year = "1974"), shelfBook(2, year = null)))

        fun announced(index: Int) = rule.onAllNodesWithTag(BookShelfTestTags.CARD)[index].fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertEquals(listOf("Book 1", "1974"), announced(0))
        assertEquals(listOf("Book 2"), announced(1))
    }

    // #105 TC-42
    @Test
    fun tc42_aLongShelfScrollsToItsLastBook() {
        setSection(books = (1..12).map { shelfBook(it) })

        rule.onNodeWithTag(BookShelfTestTags.ROW).performScrollToIndex(11)

        rule.onNodeWithTag(MoreByAuthorTestTags.SECTION).assertIsDisplayed()
        rule.onAllNodesWithTag(BookShelfTestTags.CARD)[rule.onAllNodesWithTag(BookShelfTestTags.CARD)
            .fetchSemanticsNodes().lastIndex].assertIsDisplayed()
    }

    // #105 TC-43
    @Test
    fun tc43_theSectionRendersNothingForAnAuthorWithoutAnIdAndNeedsNoHilt() {
        val book = testDetailBook(creators = listOf(Creator("Unlinked Author", "author", null, null)))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MoreByAuthorSection.Content(testSectionContext(book = book), Modifier)
            }
        }

        rule.onNodeWithTag(MoreByAuthorTestTags.SECTION).assertDoesNotExist()
    }
}
