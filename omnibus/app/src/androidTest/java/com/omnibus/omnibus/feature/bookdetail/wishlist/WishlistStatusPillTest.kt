package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingProgressSection
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingProgressTestTags
import com.omnibus.omnibus.feature.bookdetail.status.ReadStatusSection
import com.omnibus.omnibus.feature.bookdetail.status.ReadStatusTestTags
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WishlistStatusPillTest {

    @get:Rule
    val rule = createComposeRule()

    private fun present() = WishlistState.Present(WishlistEntry(id = 1, addedAtMillis = null, source = WishlistSource.Scan))

    // TC-61
    @Test
    fun issue119_tc61_aFilelessWishlistBookWithAPhysicalCopyShowsNoPill() {
        val book = testDetailBook(formats = emptyList()).copy(hasPhysical = true)
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalWishlistRepository provides fake) {
                    ReadStatusSection.Content(testSectionContext(book = book), Modifier.fillMaxWidth())
                }
            }
        }

        rule.onAllNodesWithTag(ReadStatusTestTags.PILL).assertCountEquals(0)
    }

    // TC-62
    @Test
    fun issue119_tc62_aFilelessBookWithoutAPhysicalCopyShowsNoPillAndNoAccess() {
        val book = testDetailBook(formats = emptyList())
        val fake = FakeWishlistRepository()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalWishlistRepository provides fake) {
                    ReadStatusSection.Content(testSectionContext(book = book), Modifier.fillMaxWidth())
                }
            }
        }

        val states = listOf(present(), WishlistState.Absent, WishlistState.Unknown, WishlistState.Failed)
        for (state in states) {
            rule.runOnIdle { fake.seed(book.uuid, state) }
            rule.onAllNodesWithTag(ReadStatusTestTags.PILL).assertCountEquals(0)
        }
        assertTrue("the showsReadStatus gate returns before the repository", fake.flowCalls.isEmpty())
    }

    // TC-64
    @Test
    fun issue119_tc64_aFilelessWishlistBookShowsNoProgress() {
        val book = testDetailBook(formats = emptyList()).copy(hasPhysical = true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadingProgressSection.Content(testSectionContext(book = book), Modifier.fillMaxWidth())
            }
        }

        rule.onAllNodesWithTag(ReadingProgressTestTags.BAR).assertCountEquals(0)
    }
}
