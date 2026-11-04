package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class PlayerScreenMountPointsTest : DescribeSpec({

    describe("pre-placed blocks") {

        // C11, R19
        it("#165 TC-88: every later ticket has its blocks (C11)") {
            val lines = playerScreenSource().lines()

            fun markerIndices(marker: String): List<Int> =
                lines.indices.filter { lines[it].trim() == marker }

            markerIndices("// #166").size shouldBe 1
            markerIndices("// #167").size shouldBe 2
            markerIndices("// #168").size shouldBe 2
            markerIndices("// #169").size shouldBe 2
            markerIndices("// #170").size shouldBe 2
            markerIndices("// #171").size shouldBe 2
            markerIndices("// #172").size shouldBe 1

            // The first marker after `PlayerActions(` and the first after `when (overlay)` follow those lines directly.
            val actionsStart = lines.indexOfFirst { it.trim() == "val actions = PlayerActions(" }
            val whenStart = lines.indexOfFirst { it.trim().startsWith("when (overlay)") }
            val exempt = listOf(actionsStart, whenStart).map { start ->
                lines.indices.first { it > start && lines[it].trim().startsWith("// #") }
            }

            for (ticket in listOf("// #167", "// #168", "// #169", "// #170", "// #171", "// #172")) {
                for (index in markerIndices(ticket)) {
                    if (index in exempt) continue
                    lines[index - 1].isBlank() shouldBe true
                }
            }
        }
    }
})

/** The screen's source, read from the module root whichever directory the tests run from. */
private fun playerScreenSource(): String {
    val relative = "src/main/java/com/omnibus/omnibus/feature/audiobook/player/PlayerScreen.kt"
    val candidates = listOf(File(relative), File("app/$relative"), File("omnibus/app/$relative"))
    val file = candidates.firstOrNull { it.exists() }
        ?: error("PlayerScreen.kt not found from ${File(".").absolutePath}; tried ${candidates.map { it.path }}")
    return file.readText()
}
