package com.omnibus.omnibus.feature.bookdetail.more

import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfMenuEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** One registry block: its `// #N Title` comment and its value line (`null,` or one identifier followed by `,`). */
data class RegistryBlock(val comment: String, val value: String)

private const val DECLARATION = "internal val moreMenuEntries: List<BookMoreMenuEntry> = listOfNotNull("
private val COMMENT = Regex("""// #\d+ .+""")
private val VALUE = Regex("""(null|[A-Za-z_][A-Za-z0-9_]*),""")

/** The six seeded comments, in menu order. */
private val SEEDED_COMMENTS = listOf(
    "// #116 Add to shelf",
    "// #117 Bookmarks",
    "// #118 Export",
    "// #119 Remove from wishlist",
    "// #120 Merge with",
    "// #121 Delete",
)

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

/** Fails the test with a clear message when the registry does not parse. */
private fun <T : Any> T?.parsed(): T = requireNotNull(this) { "registry body did not parse" }

class MoreMenuEntriesTest : DescribeSpec({

    val source = File(
        System.getProperty("user.dir"),
        "src/main/java/com/omnibus/omnibus/feature/bookdetail/more/MoreMenuEntries.kt",
    ).readLines()

    describe("More menu registry") {

        it("#116 TC-6: registry has the six seeded blocks in order with null or one identifier each") {
            val blocks = parseRegistry(source).parsed()
            blocks.map { it.comment } shouldBe SEEDED_COMMENTS
            blocks.first().value shouldBe "ShelfMenuEntry,"
            blocks.drop(1).map { it.value } shouldBe List(5) { "null," }
            source.any { it.trim() == DECLARATION } shouldBe true
        }

        it("#116 TC-6: a block with an extra line or reordered comments is rejected") {
            val extraLine = source.toMutableList().apply {
                add(indexOfFirst { it.trim() == "// #117 Bookmarks" } + 1, "        // extra")
            }
            parseRegistry(extraLine) shouldBe null

            val reordered = source.toMutableList().apply {
                val a = indexOfFirst { it.trim() == "// #117 Bookmarks" }
                val b = indexOfFirst { it.trim() == "// #118 Export" }
                set(a, "        // #118 Export")
                set(b, "        // #117 Bookmarks")
            }
            (parseRegistry(reordered).parsed().map { it.comment } == SEEDED_COMMENTS) shouldBe false
        }

        it("#116 TC-7: moreMenuEntries starts with the shelf entry and has one entry per non-null block") {
            val blocks = parseRegistry(source).parsed()
            moreMenuEntries.first() shouldBe ShelfMenuEntry
            moreMenuEntries.size shouldBe blocks.count { it.value != "null," }
        }

        it("#116 TC-8: BookMoreMenuEntry declares only Rows and Overlay") {
            val methods = BookMoreMenuEntry::class.java.declaredMethods
            methods.map { it.name }.toSet() shouldBe setOf("Rows", "Overlay")
            methods.size shouldBe 2
        }
    }
})
