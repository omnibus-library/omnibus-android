package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

class ReaderContentsSheetPreviewsTest : DescribeSpec({

    // Unit tests run with the module directory as the working directory.
    val source = File("src/main/java/com/omnibus/omnibus/feature/reader/ui/ReaderContentsSheetPreviews.kt").readText()

    describe("ReaderContentsSheetPreviews") {

        // CS-57
        it("CS-57: the required previews exist") {
            val previewLines = source.lines().filter { it.contains("@Preview(name = ") }
            val names = previewLines.map { Regex("""@Preview\(name = "([^"]+)"""").find(it)!!.groupValues[1] }

            (previewLines.size >= 9) shouldBe true
            previewLines.count { it.contains("UI_MODE_NIGHT_YES") } shouldBe 4
            names.count { it.contains("Nested with pages") } shouldBe 2
            names.count { it.contains("Nested without pages") } shouldBe 2
            names.count { it == "Empty" } shouldBe 1
            names.count { it.startsWith("Bookmarks empty") } shouldBe 2
            names.count { it == "Bookmarks" || it == "Bookmarks dark" } shouldBe 2
        }

        // CS-58
        it("CS-58: nested fixtures have three levels and a current chapter") {
            ReaderContentsPreviewToc.map { it.level }.toSet().shouldContainAll(setOf(0, 1, 2))
            currentTocIndex(ReaderContentsPreviewToc, ReaderContentsPreviewLocation).shouldNotBeNull()
            currentTocIndex(ReaderContentsPreviewToc, ReaderContentsPreviewBareLocation).shouldNotBeNull()

            for (entry in ReaderContentsPreviewToc) {
                (entry.page!! >= 1) shouldBe true
            }
            for (entry in ReaderContentsPreviewBareToc) {
                entry.page shouldBe null
            }
            ReaderContentsPreviewToc.size.shouldBeGreaterThanOrEqual(3)
        }
    }
})
