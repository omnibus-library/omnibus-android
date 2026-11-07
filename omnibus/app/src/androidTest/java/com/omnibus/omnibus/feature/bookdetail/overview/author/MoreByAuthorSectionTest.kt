package com.omnibus.omnibus.feature.bookdetail.overview.author

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfTestTags
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MoreByAuthorSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun shelfBook(n: Int) = AuthorShelfBook(
        uuid = "uuid-$n",
        identity = CoverIdentity(uuid = "uuid-$n", title = "Book $n", hasCover = false),
        title = "Book $n",
        year = "19$n",
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
}
