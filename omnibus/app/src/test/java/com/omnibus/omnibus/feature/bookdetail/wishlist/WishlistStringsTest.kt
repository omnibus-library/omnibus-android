package com.omnibus.omnibus.feature.bookdetail.wishlist

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class WishlistStringsTest : DescribeSpec({

    val appDir = File(System.getProperty("user.dir"))
    val valuesDir = File(appDir, "src/main/res/values")
    val wishlistStrings = File(valuesDir, "strings_book_detail_wishlist.xml")
    val stringRegex = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    val iconFile = File(appDir, "src/main/res/drawable/ic_book_detail_wishlist_remove.xml")

    /** The string names and their texts in [file], with XML's `\'` escapes undone. */
    fun stringsIn(file: File): Map<String, String> =
        stringRegex.findAll(file.readText()).associate { match ->
            match.groupValues[1] to match.groupValues[2].replace("\\'", "'")
        }

    val expectedNames = listOf(
        "book_detail_wishlist_line",
        "book_detail_wishlist_line_no_source",
        "book_detail_wishlist_source_scan",
        "book_detail_wishlist_source_detail",
        "book_detail_wishlist_source_manual",
        "book_detail_wishlist_source_search",
        "book_detail_wishlist_find_a_copy",
        "book_detail_wishlist_check_in",
        "book_detail_wishlist_remove",
        "book_detail_wishlist_confirm_title",
        "book_detail_wishlist_confirm_keeps_book",
        "book_detail_wishlist_confirm_only_place",
        "book_detail_wishlist_confirm_remove",
        "book_detail_wishlist_confirm_cancel",
        "book_detail_wishlist_removed",
        "book_detail_wishlist_remove_failed",
    )

    describe("book detail wishlist resources") {

        it("#119 TC-35: every name is prefixed book_detail_wishlist, the expected set exists, and the shared row is not redefined") {
            val text = wishlistStrings.readText()
            val names = stringRegex.findAll(text).map { it.groupValues[1] }.toList()

            names.all { it.startsWith("book_detail_wishlist_") } shouldBe true
            names shouldContainExactlyInAnyOrder expectedNames
            names.contains("book_detail_more_needs_connection") shouldBe false

            val others = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != wishlistStrings }.orEmpty()
            others.filter { it.readText().contains("name=\"book_detail_wishlist") } shouldBe emptyList()
        }

        it("#119 TC-36: apostrophes are escaped, and the only placeholder is in the line with a source") {
            val text = wishlistStrings.readText()
            val bodies = stringRegex.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

            bodies.values.forEach { body ->
                Regex("""(?<!\\)'""").containsMatchIn(body) shouldBe false
            }
            bodies.filterValues { it.contains("%1\$s") }.keys shouldBe setOf("book_detail_wishlist_line")
            stringsIn(wishlistStrings).getValue("book_detail_wishlist_remove_failed") shouldBe
                "Couldn't remove this book from your wishlist."
        }

        it("#119 TC-37: every visible string has the ticket's exact copy") {
            stringsIn(wishlistStrings) shouldContainExactly mapOf(
                "book_detail_wishlist_line" to "On your wishlist · added from %1\$s",
                "book_detail_wishlist_line_no_source" to "On your wishlist",
                "book_detail_wishlist_source_scan" to "a scan",
                "book_detail_wishlist_source_detail" to "this page",
                "book_detail_wishlist_source_manual" to "an ISBN you entered",
                "book_detail_wishlist_source_search" to "a title search",
                "book_detail_wishlist_find_a_copy" to "Find a copy",
                "book_detail_wishlist_check_in" to "Check in",
                "book_detail_wishlist_remove" to "Remove from wishlist",
                "book_detail_wishlist_confirm_title" to "Remove from wishlist?",
                "book_detail_wishlist_confirm_keeps_book" to
                    "This stops tracking a physical copy. The book stays in your library.",
                "book_detail_wishlist_confirm_only_place" to
                    "Your library holds no files for this book, so the wishlist is the only place it shows up.",
                "book_detail_wishlist_confirm_remove" to "Remove",
                "book_detail_wishlist_confirm_cancel" to "Cancel",
                "book_detail_wishlist_removed" to "Removed from your wishlist",
                "book_detail_wishlist_remove_failed" to "Couldn't remove this book from your wishlist.",
            )
        }

        it("#119 TC-38: the heart-slash icon is a 24 dp stroked vector with round caps, joins and no fill") {
            iconFile.exists() shouldBe true
            val text = iconFile.readText()

            text shouldContain "android:width=\"24dp\""
            text shouldContain "android:viewportWidth=\"24\""
            text shouldNotContain "android:fillColor"

            val paths = Regex("""<path[\s\S]*?/>""").findAll(text).map { it.value }.toList()
            paths.isEmpty() shouldBe false
            paths.forEach { path ->
                path shouldContain "android:strokeWidth=\"1.7\""
                path shouldContain "android:strokeLineCap=\"round\""
                path shouldContain "android:strokeLineJoin=\"round\""
                path shouldContain "android:strokeColor=\"#FF000000\""
            }
            paths.all { it.contains("android:pathData") } shouldBe true
        }
    }
})
