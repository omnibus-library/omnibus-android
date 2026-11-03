package com.omnibus.omnibus.feature.bookdetail.files.details

import com.omnibus.omnibus.feature.bookdetail.slots.filesSections
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class BookDetailsRegistrationTest : DescribeSpec({

    describe("the Details list's registration") {

        it("#114: it is the last section of the Files tab") {
            filesSections.last() shouldBe BookDetailsSection
        }

        it("#114: every name in strings_book_detail_details.xml is prefixed book_detail_details_") {
            val file = File(System.getProperty("user.dir"), "src/main/res/values/strings_book_detail_details.xml")
            file.exists() shouldBe true
            val names = Regex("""<(?:string|plurals) name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toList()

            (names.size >= 10) shouldBe true
            names.all { it.startsWith("book_detail_details_") } shouldBe true
        }
    }
})
