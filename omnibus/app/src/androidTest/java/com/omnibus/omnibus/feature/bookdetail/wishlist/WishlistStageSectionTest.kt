package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A fileless book: it has no ebook or audiobook, so the stage line is its only wishlist part. */
private fun filelessBook(): Book = testDetailBook(formats = emptyList())

/**
 * Renders the stage section with [fake] as the wishlist repository. [book] and [reloadKey] are read on each
 * composition, so a test can change them with `runOnIdle`.
 */
private fun ComposeContentTestRule.setStage(
    book: () -> Book,
    fake: FakeWishlistRepository,
    reloadKey: () -> Int = { 0 },
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(LocalWishlistRepository provides fake) {
                WishlistStageSection.Content(testSectionContext(book = book(), reloadKey = reloadKey()), Modifier)
            }
        }
    }
}

/** When the stage line shows, and its refreshes. WishlistLineScreenshotTest covers its wording for each source. */
class WishlistStageSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun present(source: WishlistSource?) =
        WishlistState.Present(WishlistEntry(id = 1, addedAtMillis = 1_700_000_000_000, source = source))

    // TC-45
    @Test
    fun issue119_tc45_booksWithAFileShowNoLineAndNoAccess() {
        val book = mutableStateOf(testDetailBook(formats = listOf("epub")))
        val fake = FakeWishlistRepository().apply { seed(book.value.uuid, present(WishlistSource.Scan)) }
        rule.setStage({ book.value }, fake)
        val formats = listOf(listOf("epub"), listOf("mp3"), listOf("epub", "mp3"))
        for (value in formats) {
            rule.runOnIdle { book.value = testDetailBook(formats = value) }
            rule.onAllNodesWithTag(WishlistTestTags.LINE).assertCountEquals(0)
        }
        assertTrue("no access for a book with a file", fake.flowCalls.isEmpty())
        assertTrue(fake.refreshCalls.isEmpty())
    }

    // TC-46
    @Test
    fun issue119_tc46_absentStateShowsNoLine() {
        assertNoLineFor(WishlistState.Absent)
    }

    // TC-46
    @Test
    fun issue119_tc46_unknownStateShowsNoLine() {
        assertNoLineFor(WishlistState.Unknown)
    }

    // TC-46
    @Test
    fun issue119_tc46_failedStateShowsNoLine() {
        assertNoLineFor(WishlistState.Failed)
    }

    private fun assertNoLineFor(state: WishlistState) {
        val book = filelessBook()
        val fake = FakeWishlistRepository().apply { seed(book.uuid, state) }
        rule.setStage({ book }, fake)
        rule.onAllNodesWithTag(WishlistTestTags.LINE).assertCountEquals(0)
    }

    // TC-47
    @Test
    fun issue119_tc47_lineDisappearsWhenTheStateBecomesAbsent() {
        val book = filelessBook()
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present(WishlistSource.Detail)) }
        rule.setStage({ book }, fake)
        rule.onNodeWithTag(WishlistTestTags.LINE).assertIsDisplayed()

        rule.runOnIdle { fake.seed(book.uuid, WishlistState.Absent) }
        rule.onAllNodesWithTag(WishlistTestTags.LINE).assertCountEquals(0)
    }

    // TC-48
    @Test
    fun issue119_tc48_refreshRunsOncePerNewReloadKey() {
        val book = filelessBook()
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present(WishlistSource.Scan)) }
        val key = mutableIntStateOf(0)
        rule.setStage({ book }, fake, reloadKey = { key.intValue })
        rule.waitForIdle()
        assertEquals(0, fake.refreshCalls.size)

        rule.runOnIdle { key.intValue = 1 }
        rule.waitForIdle()
        assertEquals(1, fake.refreshCalls.size)

        rule.runOnIdle { key.intValue = 2 }
        rule.waitForIdle()
        assertEquals(2, fake.refreshCalls.size)

        rule.runOnIdle { key.intValue = 2 }
        rule.waitForIdle()
        assertEquals(2, fake.refreshCalls.size)
    }
}
