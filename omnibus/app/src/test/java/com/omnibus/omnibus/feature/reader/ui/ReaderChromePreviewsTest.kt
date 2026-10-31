package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ReaderChromePreviewsTest : DescribeSpec({

    // Unit tests run with the module directory as the working directory.
    val source = File("src/main/java/com/omnibus/omnibus/feature/reader/ui/ReaderChromePreviews.kt").readText()

    describe("ReaderChromePreviews") {

        it("RC-24: the required previews exist") {
            val previewLines = source.lines().filter { it.contains("@Preview(name = ") }
            val names = previewLines.map { Regex("""@Preview\(name = "([^"]+)"""").find(it)!!.groupValues[1] }

            (previewLines.size >= 13) shouldBe true
            previewLines.count { it.contains("UI_MODE_NIGHT_YES") } shouldBe 1
            for (theme in listOf("Light", "Sepia", "Dark", "Black")) {
                names.contains("$theme hidden") shouldBe true
                names.contains("$theme visible") shouldBe true
            }
            names.count { it.contains("Menu open") } shouldBe 2
            names.count { it.contains("Fallback") } shouldBe 2
        }
    }
})
