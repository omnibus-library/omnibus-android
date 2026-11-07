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
import com.omnibus.omnibus.feature.bookdetail.status.ReadStatusSection
import com.omnibus.omnibus.feature.bookdetail.status.ReadStatusTestTags
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
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
        val fake = TestWishlistRepository().apply { seed(book.uuid, present()) }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalWishlistRepository provides fake) {
                    ReadStatusSection.Content(testSectionContext(book = book), Modifier.fillMaxWidth())
                }
            }
        }

        rule.onAllNodesWithTag(ReadStatusTestTags.PILL).assertCountEquals(0)
    }
}
