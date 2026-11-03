package com.omnibus.omnibus.feature.bookdetail.reviews

import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.reviewsSections
import com.omnibus.omnibus.feature.bookdetail.slots.stageStatusEnd
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File

class ReviewsResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")
    val slotsDir = File(System.getProperty("user.dir"), "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots")

    describe("household reviews resources") {

        it("TC-40: every review string is prefixed book_detail_reviews_") {
            val file = File(valuesDir, "strings_book_detail_reviews.xml")
            file.exists() shouldBe true
            val names = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toList()

            names.all { it.startsWith("book_detail_reviews_") } shouldBe true
            names.toSet() shouldBe setOf(
                "book_detail_reviews_stage_average",
                "book_detail_reviews_stage_none",
                "book_detail_reviews_stage_checking",
                "book_detail_reviews_stage_offline",
                "book_detail_reviews_stage_failed",
                "book_detail_reviews_stage_opens",
                "book_detail_reviews_stars_description",
                "book_detail_reviews_empty_title",
                "book_detail_reviews_empty_message",
                "book_detail_reviews_household",
                "book_detail_reviews_average",
                "book_detail_reviews_name_date",
                "book_detail_reviews_rated_only",
                "book_detail_reviews_read_more",
                "book_detail_reviews_spoiler_warning",
                "book_detail_reviews_show_review",
                "book_detail_reviews_tab_offline",
                "book_detail_reviews_tab_failed",
                "book_detail_reviews_retry",
                "book_detail_reviews_image_only",
                "book_detail_reviews_inline_spoiler",
            )
        }
    }

    describe("household reviews slots") {

        it("TC-39: both sections are the #107 lines of their slots") {
            stageStatusEnd shouldBe listOf(StageRatingSection)
            reviewsSections.count { it === HouseholdReviewsSection } shouldBe 1
            BookDetailSlots.Registered.stageStatusEnd shouldContain StageRatingSection
            BookDetailSlots.Registered.reviewsSections shouldContain HouseholdReviewsSection

            val stage = File(slotsDir, "StageSlots.kt").readLines()
            val stageLabel = stage.indexOfFirst { it.contains("// #107") }
            stage[stageLabel + 1].trim() shouldBe "com.omnibus.omnibus.feature.bookdetail.reviews.StageRatingSection,"

            val reviews = File(slotsDir, "ReviewsSections.kt").readLines()
            val label108 = reviews.indexOfFirst { it.contains("// #108") }
            val label107 = reviews.indexOfFirst { it.contains("// #107") }
            (label108 < label107) shouldBe true
            reviews[label107 + 1].trim() shouldBe "com.omnibus.omnibus.feature.bookdetail.reviews.HouseholdReviewsSection,"
        }
    }
})
