package com.omnibus.omnibus.feature.bookdetail.more

import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.delete.DeleteMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.export.ExportMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.merge.MergeMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfMenuEntry
import com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistMenuEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** One registry block: its `// #N Title` comment and its value line (`null,` or one identifier followed by `,`). */
data class RegistryBlock(val comment: String, val value: String)

private const val DECLARATION = "internal val moreMenuEntries: List<BookMoreMenuEntry> = listOfNotNull("
private val COMMENT = Regex("""// #\d+ .+""")
private val VALUE = Regex("""(null|[A-Za-z_][A-Za-z0-9_.]*),""")

/**
 * Parses the body of `moreMenuEntries` in [lines]: strictly alternating comment and value lines, up to the closing
 * `)`. Returns null when the declaration is missing or the body is not exactly that.
 */
fun parseRegistry(lines: List<String>): List<RegistryBlock>? {
    val start = lines.indexOfFirst { it.trim() == DECLARATION }
    if (start < 0) return null
    val body = lines.drop(start + 1).takeWhile { it.trim() != ")" }.map { it.trim() }
    if (body.size % 2 != 0) return null
    val blocks = mutableListOf<RegistryBlock>()
    for (i in body.indices step 2) {
        val comment = body[i]
        val value = body[i + 1]
        if (!COMMENT.matches(comment) || !VALUE.matches(value)) return null
        blocks += RegistryBlock(comment, value)
    }
    return blocks
}

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
