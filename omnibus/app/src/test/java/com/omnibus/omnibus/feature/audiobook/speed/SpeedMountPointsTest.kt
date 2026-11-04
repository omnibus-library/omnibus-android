package com.omnibus.omnibus.feature.audiobook.speed

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class SpeedMountPointsTest : DescribeSpec({

    describe("the speed sheet's mount points") {

        // R19: the sheet is mounted in the player's two #168 blocks, each one marker line with a blank line before the next
        it("#168 TC-92: the speed blocks are mounted in PlayerScreen (R19)") {
            val lines = playerScreenSource().lines()
            val markers = lines.indices.filter { lines[it].trim() == "// #168" }

            markers.size shouldBe 2
            lines[markers[0] + 1].trim() shouldBe "onOpenSpeed = { overlay = PlayerOverlay.Speed },"
            lines[markers[1] + 1].trim() shouldBe "PlayerOverlay.Speed -> SpeedSheet(onDismiss = dismissOverlay)"
            lines[markers[0] + 2].isBlank() shouldBe true
            lines[markers[1] + 2].isBlank() shouldBe true
            lines.contains("import com.omnibus.omnibus.feature.audiobook.speed.SpeedSheet") shouldBe true
        }

        // R1, R6, I-SPD-2: every dismissal path persists a live drag before the host clears the overlay
        it("#168 TC-94: the sheet persists on every dismiss path before the host closes it (R1, I-SPD-2)") {
            val source = speedSheetSource()
            val dismissAt = source.indexOf("onDismissRequest = {")
            val closeAt = source.indexOf("viewModel.onSheetClosed()", dismissAt)
            val hostDismissAt = source.indexOf("onDismiss()", closeAt)

            source.contains("skipPartiallyExpanded = true") shouldBe true
            (dismissAt >= 0 && closeAt > dismissAt && hostDismissAt > closeAt) shouldBe true
            Regex("""onDispose\s*\{\s*viewModel\.onSheetClosed\(\)\s*\}""").containsMatchIn(source) shouldBe true
        }
    }
})

/** PlayerScreen's source, read from the module root whichever directory the tests run from. */
private fun playerScreenSource(): String = sourceFile("feature/audiobook/player/PlayerScreen.kt").readText()

/** SpeedSheet's source, read from the module root whichever directory the tests run from. */
private fun speedSheetSource(): String = sourceFile("feature/audiobook/speed/SpeedSheet.kt").readText()

/** A main source file under the app's Java root, found from the module root or the repository root. */
private fun sourceFile(path: String): File {
    val relative = "src/main/java/com/omnibus/omnibus/$path"
    val candidates = listOf(File(relative), File("app/$relative"), File("omnibus/app/$relative"))
    return candidates.firstOrNull { it.exists() }
        ?: error("$path not found from ${File(".").absolutePath}; tried ${candidates.map { it.path }}")
}
