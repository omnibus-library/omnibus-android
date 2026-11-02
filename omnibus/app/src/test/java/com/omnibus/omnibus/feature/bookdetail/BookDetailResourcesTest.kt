package com.omnibus.omnibus.feature.bookdetail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File

class BookDetailResourcesTest : DescribeSpec({

    val appDir = File(System.getProperty("user.dir"))
    val valuesDir = File(appDir, "src/main/res/values")
    val nameRegex = Regex("""<string name="([^"]+)"""")

    describe("book detail resources") {

        it("#93 TC-50: strings live in strings_book_detail.xml and the stub is gone") {
            val bookDetailStrings = File(valuesDir, "strings_book_detail.xml")
            bookDetailStrings.exists() shouldBe true
            val names = nameRegex.findAll(bookDetailStrings.readText()).map { it.groupValues[1] }.toList()

            names.all { it.startsWith("book_detail_") } shouldBe true
            names shouldContainExactlyInAnyOrder listOf(
                "book_detail_loading",
                "book_detail_error_title",
                "book_detail_error_not_found",
                "book_detail_retry",
                "book_detail_back",
                "book_detail_cover_description",
                "book_detail_author_description",
                "book_detail_author_click_label",
                "book_detail_series_line",
                "book_detail_series_description",
                "book_detail_year_beside",
                "book_detail_read",
                "book_detail_placeholder_check_in",
                "book_detail_placeholder_settings",
                "book_detail_placeholder_message",
            )

            val strings = nameRegex.findAll(File(valuesDir, "strings.xml").readText()).map { it.groupValues[1] }
            strings.none { it.startsWith("book_detail_") } shouldBe true

            File(appDir, "src/main/java/com/omnibus/omnibus/feature/library/ui/BookDetailStubScreen.kt").exists() shouldBe false
            File(appDir, "src/androidTest/java/com/omnibus/omnibus/feature/library/ui/BookDetailStubTest.kt").exists() shouldBe false
        }
    }
})
