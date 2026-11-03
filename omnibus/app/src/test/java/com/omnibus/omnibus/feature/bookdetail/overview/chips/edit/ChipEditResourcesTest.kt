package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ChipEditResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val file = File(valuesDir, "strings_book_detail_chipedit.xml")

    /** The text of string [name], with the XML escapes for an apostrophe and a quote undone. */
    fun text(name: String): String {
        val match = Regex("""<string name="${Regex.escape(name)}">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(file.readText())
        checkNotNull(match) { "No string named $name" }
        return match.groupValues[1].replace("\\'", "'").replace("\\\"", "\"")
    }

    /** The text of the plural item for [quantity] of the plural named [name]. */
    fun plural(name: String, quantity: String): String {
        val block = Regex("""<plurals name="${Regex.escape(name)}">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .find(file.readText())
        checkNotNull(block) { "No plural named $name" }
        val item = Regex("""<item quantity="$quantity">(.*?)</item>""").find(block.groupValues[1])
        return checkNotNull(item).groupValues[1]
    }

    /** Every string or plural name declared in [source]. */
    fun namesIn(source: File): List<String> =
        Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(source.readText()).map { it.groupValues[1] }.toList()

    describe("strings") {

        it("TC-58: the add chips are named Add genres and Add tags") {
            text("book_detail_chipedit_add_genres") shouldBe "Add genres"
            text("book_detail_chipedit_add_tags") shouldBe "Add tags"
        }

        it("TC-59: field labels, title, hint and create rows match the spec") {
            text("book_detail_chipedit_field_genres") shouldBe "Add a genre…"
            text("book_detail_chipedit_field_tags") shouldBe "Add a tag…"
            text("book_detail_chipedit_title") shouldBe "%1\$s · %2\$s"
            text("book_detail_chipedit_hint") shouldBe "Tap a chip to remove it · changes save as you go"
            text("book_detail_chipedit_empty") shouldBe "Nothing here yet."
            text("book_detail_chipedit_create_genre") shouldBe "Add “%1\$s” as a new genre"
            text("book_detail_chipedit_create_tag") shouldBe "Add “%1\$s” as a new tag"
        }

        it("TC-60: remove, failure, offline, full and book count copy match the spec") {
            text("book_detail_chipedit_remove") shouldBe "Remove %1\$s"
            text("book_detail_chipedit_failed_genres") shouldBe "Couldn't save the genres. They're back as they were."
            text("book_detail_chipedit_failed_tags") shouldBe "Couldn't save the tags. They're back as they were."
            text("book_detail_chipedit_offline") shouldBe "You're offline. Changes need a connection."
            text("book_detail_chipedit_full") shouldBe "A book can have up to 64."
            plural("book_detail_chipedit_book_count", "one") shouldBe "%1\$d book"
            plural("book_detail_chipedit_book_count", "other") shouldBe "%1\$d books"
        }

        it("TC-61: names are prefixed and unique across the values files") {
            val own = namesIn(file)
            own.all { it.startsWith("book_detail_chipedit_") } shouldBe true
            own.size shouldBe own.toSet().size

            val others = valuesDir.listFiles { f -> f.name.endsWith(".xml") && f != file }.orEmpty()
                .flatMap { namesIn(it) }
                .toSet()
            own.filter { it in others } shouldBe emptyList()
        }
    }
})
