package com.omnibus.omnibus.feature.bookdetail.more

import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.delete.DeleteMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.export.ExportMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.merge.MergeMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfMenuEntry
import com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistMenuEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MoreMenuEntriesTest : DescribeSpec({

    describe("the entries of the More menu") {
        it("#116 TC-7, #117 TC-3, #118 TC-58, #120 TC-9, #121 TC-17: run from shelves through to delete, in menu order") {
            moreMenuEntries shouldBe listOf(
                ShelfMenuEntry,
                BookmarksMenuEntry,
                ExportMenuEntry,
                WishlistMenuEntry,
                MergeMenuEntry,
                DeleteMenuEntry,
            )
        }

        it("#117 TC-3: join only bookmarks to the group above") {
            moreMenuEntries.filter { it.joinsPreviousGroup } shouldBe listOf(BookmarksMenuEntry)
        }
    }
})
