package com.omnibus.omnibus.feature.bookdetail.more

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File

class MoreStringsTest : DescribeSpec({

    val appDir = File(System.getProperty("user.dir"))
    val valuesDir = File(appDir, "src/main/res/values")
    val moreStrings = File(valuesDir, "strings_book_detail_more.xml")
    val stringRegex = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    val pluralsRegex = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    val quantityRegex = Regex("""<item quantity="(\w+)">""")

    val expectedStrings = listOf(
        "book_detail_more",
        "book_detail_more_needs_connection",
        "book_detail_more_add_to_shelf",
        "book_detail_more_shelf_close",
        "book_detail_more_shelf_intro",
        "book_detail_more_shelf_empty",
        "book_detail_more_shelf_loading",
        "book_detail_more_shelf_error",
        "book_detail_more_shelf_retry",
        "book_detail_more_shelf_meta",
        "book_detail_more_shelf_smart_title",
        "book_detail_more_shelf_smart_note",
        "book_detail_more_shelf_add_failed",
        "book_detail_more_shelf_remove_failed",
        "book_detail_more_shelf_add_action",
        "book_detail_more_shelf_remove_action",
        "book_detail_more_rule_field_tag",
        "book_detail_more_rule_field_genre",
        "book_detail_more_rule_field_author",
        "book_detail_more_rule_field_series",
        "book_detail_more_rule_field_rating",
        "book_detail_more_rule_field_status",
        "book_detail_more_rule_field_format",
        "book_detail_more_rule_field_year",
        "book_detail_more_rule_field_date_added",
        "book_detail_more_rule_field_date_updated",
        "book_detail_more_rule_op_is",
        "book_detail_more_rule_op_is_not",
        "book_detail_more_rule_op_contains",
        "book_detail_more_rule_op_starts_with",
        "book_detail_more_rule_op_gte",
        "book_detail_more_rule_op_includes",
        "book_detail_more_rule_op_in_last",
        "book_detail_more_rule_op_before",
        "book_detail_more_rule_op_after",
        "book_detail_more_rule_op_between",
        "book_detail_more_rule_status_unread",
        "book_detail_more_rule_status_reading",
        "book_detail_more_rule_status_finished",
        "book_detail_more_rule_clause",
        "book_detail_more_rule_and",
        "book_detail_more_rule_or",
        "book_detail_more_rule_more",
    )
    val expectedPlurals = listOf(
        "book_detail_more_shelf_book_count",
        "book_detail_more_rule_unit_days",
        "book_detail_more_rule_unit_weeks",
        "book_detail_more_rule_unit_months",
        "book_detail_more_rule_unit_years",
    )

    /** The visible copies that must come from resources, never a Kotlin literal. */
    val visibleCopies = listOf("Add to shelf", "Needs a connection", "More", "Close", "Smart shelves", "Try again")

    describe("more menu strings") {

        it("#116 TC-65: every name is prefixed book_detail_more and the expected set exists") {
            val text = moreStrings.readText()
            val names = stringRegex.findAll(text).map { it.groupValues[1] }.toList() +
                pluralsRegex.findAll(text).map { it.groupValues[1] }.toList()

            names.all { it == "book_detail_more" || it.startsWith("book_detail_more_") } shouldBe true
            names shouldContainExactlyInAnyOrder expectedStrings + expectedPlurals
        }

        it("#116 TC-65: book_detail_more_shelf_book_count has one and other, and unit plurals too") {
            val text = moreStrings.readText()
            for (name in expectedPlurals) {
                val body = pluralsRegex.findAll(text).first { it.groupValues[1] == name }.groupValues[2]
                quantityRegex.findAll(body).map { it.groupValues[1] }.toSet() shouldBe setOf("one", "other")
            }
        }

        it("#116 TC-65: apostrophes are escaped in every string") {
            val text = moreStrings.readText()
            val bodies = stringRegex.findAll(text).map { it.groupValues[2] } +
                pluralsRegex.findAll(text).map { it.groupValues[2] }
            bodies.forEach { body ->
                Regex("""(?<!\\)'""").containsMatchIn(body) shouldBe false
            }
        }

        it("#116 TC-65: no other values file defines a book_detail_more name") {
            val others = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != moreStrings }.orEmpty()
            others.filter { it.readText().contains("name=\"book_detail_more") } shouldBe emptyList()
        }

        it("#116 TC-65: no Kotlin file in the More feature hard-codes a visible copy") {
            val feature = File(appDir, "src/main/java/com/omnibus/omnibus/feature/bookdetail/more")
            val kotlinFiles = if (feature.exists()) feature.walkTopDown().filter { it.extension == "kt" }.toList() else emptyList()
            val violations = kotlinFiles.flatMap { file ->
                visibleCopies.filter { copy -> file.readText().contains("\"$copy\"") }.map { "${file.name}: $it" }
            }
            violations.shouldBeEmpty()
        }
    }
})
