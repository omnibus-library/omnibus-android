package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ReviewSheetResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val file = File(valuesDir, "strings_book_detail_review_sheet.xml")

    /** Every `<string>` entry in the file, in order, with `\'` read as a plain apostrophe. */
    fun entries(): List<Pair<String, String>> =
        Regex("""<string name="([^"]+)">(.*)</string>""").findAll(file.readText())
            .map { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
            .toList()

    /** The string names and their values in the file. */
    fun strings(): Map<String, String> = entries().toMap()

    describe("review sheet resources") {

        it("TC-65: the file exists, every name has the prefix and none is empty or repeated, and no other file has one") {
            file.exists() shouldBe true
            val names = entries().map { it.first }

            names.all { it.startsWith("book_detail_review_sheet_") } shouldBe true
            entries().all { it.second.isNotEmpty() } shouldBe true
            names.distinct().size shouldBe names.size

            valuesDir.listFiles { f -> f.name.endsWith(".xml") && f != file }!!.forEach { other ->
                other.readText().contains("book_detail_review_sheet_") shouldBe false
            }
        }

        it("TC-66: the values are the copy the plan gives, with the placeholders in place") {
            strings() shouldBe mapOf(
                "book_detail_review_sheet_title_write" to "Your review",
                "book_detail_review_sheet_title_edit" to "Edit review",
                "book_detail_review_sheet_cancel" to "Cancel",
                "book_detail_review_sheet_save" to "Save",
                "book_detail_review_sheet_stars_description" to "Your rating, in half stars",
                "book_detail_review_sheet_stars_state" to "%1\$s of 5 stars, %2\$s",
                "book_detail_review_sheet_stars_unrated" to "Not rated",
                "book_detail_review_sheet_readout_prompt" to "Tap or drag. Half stars count.",
                "book_detail_review_sheet_readout_compact" to "%1\$s · %2\$s",
                "book_detail_review_sheet_readout_compact_unrated" to "Not rated yet",
                "book_detail_review_sheet_word_not_for_me" to "Not for me",
                "book_detail_review_sheet_word_didnt_work" to "Didn't work for me",
                "book_detail_review_sheet_word_hard_going" to "Hard going",
                "book_detail_review_sheet_word_had_its_moments" to "Had its moments",
                "book_detail_review_sheet_word_mixed_feelings" to "Mixed feelings",
                "book_detail_review_sheet_word_liked_it" to "Liked it",
                "book_detail_review_sheet_word_liked_it_a_lot" to "Liked it a lot",
                "book_detail_review_sheet_word_really_good" to "Really good",
                "book_detail_review_sheet_word_loved_it" to "Loved it",
                "book_detail_review_sheet_word_a_favorite" to "A favorite",
                "book_detail_review_sheet_review_label" to "Review",
                "book_detail_review_sheet_review_audience" to "Your household can read this",
                "book_detail_review_sheet_review_placeholder" to "What did you make of it?",
                "book_detail_review_sheet_from_highlights" to "From highlights",
                "book_detail_review_sheet_accessory_highlights" to "Highlights",
                "book_detail_review_sheet_accessory_done" to "Done",
                "book_detail_review_sheet_spoiler" to "This review gives away the story.",
                "book_detail_review_sheet_notes_label" to "Private notes",
                "book_detail_review_sheet_notes_hint" to "Only you can see these",
                "book_detail_review_sheet_notes_placeholder" to "Notes to yourself",
                "book_detail_review_sheet_conflict_rating" to
                    "Your rating changed elsewhere while this was open. Save keeps yours.",
                "book_detail_review_sheet_conflict_text" to
                    "Your review text changed elsewhere while this was open. Save keeps yours.",
                "book_detail_review_sheet_conflict_notes" to
                    "Your private notes changed elsewhere while this was open. Save keeps yours.",
                "book_detail_review_sheet_conflict_spoiler" to
                    "Your spoiler setting changed elsewhere while this was open. Save keeps yours.",
                "book_detail_review_sheet_conflict_several" to
                    "Parts of your review changed elsewhere while this was open. Save keeps yours.",
                "book_detail_review_sheet_save_failed" to "Your review wasn't saved.",
                "book_detail_review_sheet_save_failed_offline" to "No connection. Your review wasn't saved.",
                "book_detail_review_sheet_discard_title" to "Discard your review?",
                "book_detail_review_sheet_discard_message" to "Your stars and what you wrote here won't be saved.",
                "book_detail_review_sheet_discard_confirm" to "Discard",
                "book_detail_review_sheet_discard_keep" to "Keep editing",
                "book_detail_review_sheet_picker_title" to "From your highlights",
                "book_detail_review_sheet_picker_subtitle" to "Tap one to quote it in your review.",
                "book_detail_review_sheet_picker_close" to "Close",
                "book_detail_review_sheet_picker_pick" to "Quote in your review",
                "book_detail_review_sheet_picker_loading" to "Loading your highlights…",
                "book_detail_review_sheet_picker_failed_title" to "Couldn't load your highlights",
                "book_detail_review_sheet_picker_failed_message" to "Check your connection and try again.",
                "book_detail_review_sheet_picker_retry" to "Retry",
                "book_detail_review_sheet_picker_empty_title" to "No highlights yet",
                "book_detail_review_sheet_picker_empty_message" to
                    "Highlight a passage while you read and it will show up here.",
            )
        }

        it("TC-67: only the stars state and the compact readout take format arguments, and both take two") {
            val values = strings()
            val withArgs = setOf("book_detail_review_sheet_stars_state", "book_detail_review_sheet_readout_compact")

            withArgs.forEach { name ->
                values.getValue(name).contains("%1\$s") shouldBe true
                values.getValue(name).contains("%2\$s") shouldBe true
            }
            values.filterKeys { it !in withArgs }.values.none { it.contains('%') } shouldBe true
        }
    }
})
