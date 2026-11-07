package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WishlistStateHelpersTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-50
    @Test
    fun issue119_tc50_aBookWithAFileNeverTouchesTheRepository() {
        val book = testDetailBook(formats = listOf("epub"))
        val present = WishlistState.Present(WishlistEntry(id = 1, addedAtMillis = null, source = WishlistSource.Scan))
        val fake = TestWishlistRepository().apply { seed(book.uuid, present) }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalWishlistRepository provides fake) {
                    rememberIsWishlistOnly(book)
                    rememberWishlistActionModel(book)
                }
            }
        }

        rule.waitForIdle()
        assertTrue("no repository access for a book with a file", fake.flowCalls.isEmpty())
    }
}
