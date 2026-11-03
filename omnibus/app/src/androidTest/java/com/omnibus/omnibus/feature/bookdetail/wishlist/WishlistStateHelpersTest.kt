package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.actionbar.ActionBarModel
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What the helpers returned on the last composition. */
private class HelperResults {
    var isWishlistOnly = false
    var actionModel: ActionBarModel.Wishlist? = null
}

/** Calls the helpers for [book] and records what they return. */
@Composable
private fun HelperProbe(book: Book, results: HelperResults) {
    results.isWishlistOnly = rememberIsWishlistOnly(book)
    results.actionModel = rememberWishlistActionModel(book)
}

private fun ComposeContentTestRule.setHelpers(
    book: Book,
    results: HelperResults,
    fake: FakeWishlistRepository?,
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(LocalWishlistRepository provides fake) {
                HelperProbe(book, results)
            }
        }
    }
}

class WishlistStateHelpersTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-49
    @Test
    fun issue119_tc49_inspectionModeGivesFalseAndNullWithoutARepository() {
        val book = testDetailBook(formats = emptyList())
        val results = HelperResults()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    HelperProbe(book, results)
                    WishlistStageSection.Content(testSectionContext(book = book), Modifier)
                }
            }
        }

        rule.waitForIdle()
        rule.onAllNodesWithTag(WishlistTestTags.LINE).assertCountEquals(0)
        assertFalse(results.isWishlistOnly)
        assertNull(results.actionModel)
    }

    // TC-50
    @Test
    fun issue119_tc50_aBookWithAFileIsNeverWishlistOnlyAndNeverTouchesTheRepository() {
        val book = testDetailBook(formats = listOf("epub"))
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        val results = HelperResults()
        rule.setHelpers(book, results, fake)

        rule.waitForIdle()
        assertFalse(results.isWishlistOnly)
        assertNull(results.actionModel)
        assertTrue("no repository access for a book with a file", fake.flowCalls.isEmpty())
    }

    // TC-50
    @Test
    fun issue119_tc50_aFilelessPresentBookIsWishlistOnlyWithAnActionModel() {
        val book = testDetailBook(formats = emptyList()).copy(isbn13 = "9781234567897")
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        val results = HelperResults()
        rule.setHelpers(book, results, fake)

        rule.waitForIdle()
        assertTrue(results.isWishlistOnly)
        assertEquals(
            BookDetailDestination.ExternalUrl("https://www.amazon.com/s?k=9781234567897"),
            results.actionModel?.findCopy,
        )
        assertEquals(BookDetailDestination.CheckIn(book.uuid), results.actionModel?.checkIn)
    }

    // TC-50
    @Test
    fun issue119_tc50_aFilelessAbsentBookIsNotWishlistOnly() {
        assertNotWishlistOnly(WishlistState.Absent)
    }

    // TC-50
    @Test
    fun issue119_tc50_aFilelessUnknownBookIsNotWishlistOnly() {
        assertNotWishlistOnly(WishlistState.Unknown)
    }

    // TC-50
    @Test
    fun issue119_tc50_aFilelessFailedBookIsNotWishlistOnly() {
        assertNotWishlistOnly(WishlistState.Failed)
    }

    private fun assertNotWishlistOnly(state: WishlistState) {
        val book = testDetailBook(formats = emptyList())
        val fake = FakeWishlistRepository().apply { seed(book.uuid, state) }
        val results = HelperResults()
        rule.setHelpers(book, results, fake)

        rule.waitForIdle()
        assertFalse(results.isWishlistOnly)
        assertNull(results.actionModel)
    }

    private fun present() = WishlistState.Present(WishlistEntry(id = 1, addedAtMillis = null, source = WishlistSource.Scan))
}
