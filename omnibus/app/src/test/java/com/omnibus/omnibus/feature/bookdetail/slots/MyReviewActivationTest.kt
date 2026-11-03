package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.reviews.HouseholdReviewsSection
import com.omnibus.omnibus.feature.bookdetail.reviews.mine.MyReviewSection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File

class MyReviewActivationTest : DescribeSpec({

    val slotsDir = File(System.getProperty("user.dir"), "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots")

    describe("your review slot") {

        it("TC-52: the Reviews tab's first slot registers the your review section, ahead of the household list") {
            reviewsSections.count { it === MyReviewSection } shouldBe 1
            (reviewsSections.indexOf(MyReviewSection) < reviewsSections.indexOf(HouseholdReviewsSection)) shouldBe true
            BookDetailSlots.Registered.reviewsSections shouldContain MyReviewSection

            val lines = File(slotsDir, "ReviewsSections.kt").readLines()
            val label108 = lines.indexOfFirst { it.contains("// #108") }
            val label107 = lines.indexOfFirst { it.contains("// #107") }
            lines[label108 + 1].trim() shouldBe "com.omnibus.omnibus.feature.bookdetail.reviews.mine.MyReviewSection,"
            lines[label107 + 1].trim() shouldBe "com.omnibus.omnibus.feature.bookdetail.reviews.HouseholdReviewsSection,"
            lines.filter { it.trimStart().startsWith("import ") } shouldBe
                listOf("import com.omnibus.omnibus.feature.bookdetail.BookDetailSection")
        }
    }
})
