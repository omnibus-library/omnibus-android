package com.omnibus.omnibus.feature.bookdetail.overview.activity

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class HouseholdActivityResourcesTest : DescribeSpec({

    val valuesDir = File(System.getProperty("user.dir"), "src/main/res/values")

    describe("activity resources") {

        it("TC-14: every name in strings_book_detail_activity.xml is prefixed book_detail_activity_") {
            val file = File(valuesDir, "strings_book_detail_activity.xml")
            file.exists() shouldBe true
            val names = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toList()

            (names.size >= 4) shouldBe true
            names.all { it.startsWith("book_detail_activity_") } shouldBe true
        }

        it("TC-15: the section is the #103 line of the Overview slot") {
            val slots = File(System.getProperty("user.dir"), "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots/OverviewSections.kt").readLines()
            val label = slots.indexOfFirst { it.contains("// #103") }
            slots[label + 1].trim() shouldBe "com.omnibus.omnibus.feature.bookdetail.overview.activity.HouseholdActivitySection,"
        }
    }
})
