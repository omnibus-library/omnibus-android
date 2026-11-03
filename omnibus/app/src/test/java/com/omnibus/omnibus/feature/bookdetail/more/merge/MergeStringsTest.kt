package com.omnibus.omnibus.feature.bookdetail.more.merge

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class MergeStringsTest : DescribeSpec({

    val appDir = File(System.getProperty("user.dir"))
    val valuesDir = File(appDir, "src/main/res/values")
    val mergeStrings = File(valuesDir, "strings_book_detail_merge.xml")
    val stringRegex = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    val pluralsRegex = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    val itemRegex = Regex("""<item quantity="([^"]+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    val chevron = File(appDir, "src/main/res/drawable/ic_book_detail_merge_chevron.xml")
    val arrow = File(appDir, "src/main/res/drawable/ic_book_detail_merge_arrow.xml")
    val mergeIcon = File(appDir, "src/main/res/drawable/ic_book_detail_merge.xml")

    /** The texts of the plain strings in [file], with XML's `\'` escapes undone. */
    fun stringsIn(file: File): Map<String, String> =
        stringRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].replace("\\'", "'")
        }

    /** Every name the merge file defines: plain strings and plurals. */
    fun namesIn(file: File): List<String> =
        stringRegex.findAll(file.readText()).map { it.groupValues[1] }.toList() +
            pluralsRegex.findAll(file.readText()).map { it.groupValues[1] }.toList()

    val expectedNames = listOf(
        "book_detail_merge_menu_row",
        "book_detail_merge_close",
        "book_detail_merge_intro",
        "book_detail_merge_folds_into",
        "book_detail_merge_entry_stays",
        "book_detail_merge_search_label",
        "book_detail_merge_search_hint",
        "book_detail_merge_note_idle",
        "book_detail_merge_note_count",
        "book_detail_merge_note_none",
        "book_detail_merge_searching",
        "book_detail_merge_search_failed",
        "book_detail_merge_retry",
        "book_detail_merge_same_series",
        "book_detail_merge_confirm_body",
        "book_detail_merge_back",
        "book_detail_merge_confirm",
        "book_detail_merge_merging",
        "book_detail_merge_failed",
        "book_detail_merge_failed_gone",
        "book_detail_merge_done",
        "book_detail_merge_undo",
        "book_detail_merge_undone",
        "book_detail_merge_undo_failed",
        "book_detail_merge_undo_expired",
        "book_detail_merge_choose_action",
    )

    describe("book detail merge resources") {

        it("#120 TC-10: the names are the expected set, prefixed, and defined only in the merge file") {
            val names = namesIn(mergeStrings)

            names.all { it.startsWith("book_detail_merge_") } shouldBe true
            names shouldContainExactlyInAnyOrder expectedNames

            val others = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != mergeStrings }.orEmpty()
            others.filter { it.readText().contains("name=\"book_detail_merge_") } shouldBe emptyList()
        }

        it("#120 TC-10: the shared offline text is reused, not redefined or copied") {
            namesIn(mergeStrings).contains("book_detail_more_needs_connection") shouldBe false
            stringsIn(mergeStrings).values.contains("Needs a connection") shouldBe false
        }

        it("#120 TC-11: the copy uses the ticket's characters: an ellipsis, curly quotes and escaped apostrophes") {
            val text = mergeStrings.readText()
            val plain = stringsIn(mergeStrings)

            plain.getValue("book_detail_merge_menu_row").endsWith("…") shouldBe true
            plain.getValue("book_detail_merge_merging") shouldBe "Merging…"
            plain.getValue("book_detail_merge_note_none") shouldContain "“%1\$s”"
            plain.getValue("book_detail_merge_confirm_body") shouldContain "“%1\$s”"
            plain.getValue("book_detail_merge_confirm_body") shouldContain "“%2\$s”"
            plain.getValue("book_detail_merge_search_failed") shouldBe "Couldn't search your library."
            plain.getValue("book_detail_merge_undo_expired") shouldBe "This merge can't be undone anymore."

            text.lines().filter { it.contains("<string") || it.contains("<item") }.forEach { line ->
                Regex("""(?<!\\)'""").containsMatchIn(line) shouldBe false
            }
        }

        it("#120 TC-11: placeholders are well formed, and the count plural has one and other") {
            val plain = stringsIn(mergeStrings)
            val confirm = plain.getValue("book_detail_merge_confirm_body")

            confirm.split("%1\$s").size - 1 shouldBe 1
            confirm.split("%2\$s").size - 1 shouldBe 1
            plain.getValue("book_detail_merge_note_none").split("%1\$s").size - 1 shouldBe 1

            val plural = pluralsRegex.find(mergeStrings.readText())!!.groupValues[2]
            val items = itemRegex.findAll(plural).associate { it.groupValues[1] to it.groupValues[2] }
            items.keys shouldContainExactlyInAnyOrder listOf("one", "other")
            items.values.forEach { it shouldContain "%d" }
        }

        it("#120 TC-12: the merge icon is a 24 dp stroked vector with the design's path") {
            val text = mergeIcon.readText()

            text shouldContain "android:viewportWidth=\"24\""
            text shouldContain "android:strokeWidth=\"1.7\""
            text shouldContain "android:pathData=\"M7,4v5c0,3 5,4 5,7v4M17,4v5c0,3 -5,4 -5,7M4.5,6.5L7,4l2.5,2.5M14.5,6.5L17,4l2.5,2.5\""
            text shouldNotContain "android:fillColor"
        }

        it("#120 TC-12: the chevron and the arrow exist and mirror in right-to-left layouts") {
            chevron.readText() shouldContain "android:autoMirrored=\"true\""
            arrow.readText() shouldContain "android:autoMirrored=\"true\""
        }
    }
})
