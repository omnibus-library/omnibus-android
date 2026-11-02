package com.omnibus.omnibus.feature.bookdetail.more

import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.topBarActions
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** The source of `BD/slots/TopBarSlots.kt`. Gradle runs JVM tests with the working directory at `omnibus/app`. */
private val TOP_BAR_SLOTS =
    File(System.getProperty("user.dir"), "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots/TopBarSlots.kt")

private const val LIST_OPEN = "internal val topBarActions: List<BookDetailSection> = listOfNotNull("
private const val BUTTON_IMPORT = "import com.omnibus.omnibus.feature.bookdetail.more.BookMoreButton"

/** The non-blank, trimmed lines between the list's opening line and its closing `)`, or null without the opening. */
internal fun topBarBody(lines: List<String>): List<String>? {
    val start = lines.indexOfFirst { it.trim() == LIST_OPEN }
    if (start < 0) return null
    return lines.drop(start + 1).takeWhile { it.trim() != ")" }.map { it.trim() }.filter { it.isNotEmpty() }
}

class MoreTopBarSlotTest : DescribeSpec({
    describe("the top bar slots") {
        it("#116 TC-64: only the 116 line of TopBarSlots is activated") {
            val lines = TOP_BAR_SLOTS.readLines()

            topBarBody(lines) shouldBe listOf("// #116 Add to Shelf", "BookMoreButton,")
            lines.any { it.trim() == BUTTON_IMPORT } shouldBe true
            topBarActions shouldBe listOf(BookMoreButton)
            BookDetailSlots.Registered.topBarActions shouldBe listOf(BookMoreButton)
        }
    }
})
