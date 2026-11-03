package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File

class MyReviewResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val file = File(valuesDir, "strings_book_detail_rate.xml")

    /** The string names and their values in the file, with `\'` read as a plain apostrophe. */
    fun strings(): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*)</string>""").findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }

    describe("your review resources") {

        it("TC-50: every string is prefixed book_detail_rate_, the names are the planned set, and no other file has one") {
            file.exists() shouldBe true
            val names = strings().keys

            names.all { it.startsWith("book_detail_rate_") } shouldBe true
            names shouldContainExactlyInAnyOrder listOf(
                "book_detail_rate_title",
                "book_detail_rate_note_unrated",
                "book_detail_rate_note_rated",
                "book_detail_rate_note_loading",
                "book_detail_rate_note_offline",
                "book_detail_rate_note_failed",
                "book_detail_rate_write",
                "book_detail_rate_edit",
                "book_detail_rate_delete",
                "book_detail_rate_delete_title",
                "book_detail_rate_delete_message",
                "book_detail_rate_delete_confirm",
                "book_detail_rate_delete_cancel",
                "book_detail_rate_private_notes",
                "book_detail_rate_prompt",
                "book_detail_rate_stars_description",
                "book_detail_rate_stars_state",
                "book_detail_rate_stars_unrated",
                "book_detail_rate_save_failed_offline",
                "book_detail_rate_save_failed",
                "book_detail_rate_delete_failed_offline",
                "book_detail_rate_delete_failed",
            )

            valuesDir.listFiles { f -> f.name.endsWith(".xml") && f != file }!!.forEach { other ->
                other.readText().contains("book_detail_rate_") shouldBe false
            }
        }

        it("TC-51: the values are the copy the plan gives, with the placeholders in place") {
            strings() shouldBe mapOf(
                "book_detail_rate_title" to "Your review",
                "book_detail_rate_note_unrated" to "Not rated yet. Tap or drag the stars, half stars count.",
                "book_detail_rate_note_rated" to "You rated it %1\$s",
                "book_detail_rate_note_loading" to "Loading your review…",
                "book_detail_rate_note_offline" to "Loads when you're back online.",
                "book_detail_rate_note_failed" to "Couldn't load. Pull down to retry.",
                "book_detail_rate_write" to "Write review",
                "book_detail_rate_edit" to "Edit review",
                "book_detail_rate_delete" to "Delete review",
                "book_detail_rate_delete_title" to "Delete your review?",
                "book_detail_rate_delete_message" to
                    "This removes your stars, your review and your private notes for good. This can't be undone.",
                "book_detail_rate_delete_confirm" to "Delete",
                "book_detail_rate_delete_cancel" to "Cancel",
                "book_detail_rate_private_notes" to "Private notes. Only you can see these.",
                "book_detail_rate_prompt" to "What did you make of it?",
                "book_detail_rate_stars_description" to "Your rating, in half stars",
                "book_detail_rate_stars_state" to "%1\$s of 5 stars",
                "book_detail_rate_stars_unrated" to "Not rated",
                "book_detail_rate_save_failed_offline" to "No connection. Your rating wasn't saved.",
                "book_detail_rate_save_failed" to "Your rating wasn't saved.",
                "book_detail_rate_delete_failed_offline" to "No connection. Your review wasn't deleted.",
                "book_detail_rate_delete_failed" to "Your review wasn't deleted.",
            )
        }
    }
})
