package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.actionBar
import com.omnibus.omnibus.feature.bookdetail.slots.filesSections
import com.omnibus.omnibus.feature.bookdetail.slots.highlightsSections
import com.omnibus.omnibus.feature.bookdetail.slots.overviewSections
import com.omnibus.omnibus.feature.bookdetail.slots.reviewsSections
import com.omnibus.omnibus.feature.bookdetail.slots.screenOverlays
import com.omnibus.omnibus.feature.bookdetail.slots.stageAboveTitle
import com.omnibus.omnibus.feature.bookdetail.slots.stageCoverOverlay
import com.omnibus.omnibus.feature.bookdetail.slots.stageProgress
import com.omnibus.omnibus.feature.bookdetail.slots.stageStatusEnd
import com.omnibus.omnibus.feature.bookdetail.slots.stageStatusStart
import com.omnibus.omnibus.feature.bookdetail.slots.topBarActions
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.File

private data class Registry(val file: String, val name: String, val tickets: List<String>)

/** The R23 seeded blocks, in display order: each registry's file, name and tickets. */
private val registries = listOf(
    Registry("StageSlots", "stageAboveTitle", listOf("#119 Wishlist Books")),
    Registry("StageSlots", "stageStatusStart", listOf("#95 Set Reading Status")),
    Registry("StageSlots", "stageStatusEnd", listOf("#107 Household Ratings and Reviews")),
    Registry("StageSlots", "stageProgress", listOf("#96 See Reading Progress", "#97 Finish Card and Celebration")),
    Registry("StageSlots", "stageCoverOverlay", listOf("#97 Finish Card and Celebration")),
    Registry("TopBarSlots", "topBarActions", listOf("#116 Add to Shelf")),
    Registry(
        "OverviewSections",
        "overviewSections",
        listOf(
            "#94 Book Tabs and Description",
            "#100 Genres and Tags",
            "#102 Your Reading Card",
            "#103 Household Activity",
            "#104 Series Fan",
            "#105 More by This Author",
            "#106 Readers Also Read",
        ),
    ),
    Registry("ReviewsSections", "reviewsSections", listOf("#108 Rate a Book", "#107 Household Ratings and Reviews")),
    Registry("HighlightsSections", "highlightsSections", listOf("#110 Highlights List", "#112 Journal List")),
    Registry(
        "FilesSections",
        "filesSections",
        listOf("#113 Ebook and Audiobook Downloads", "#115 Link Ebook and Audiobook Positions", "#114 Book Details"),
    ),
    Registry("ActionBarSlots", "actionBar", listOf("#98 Start, Continue and Read Again Button")),
    Registry("OverlaySlots", "screenOverlays", listOf("#109 Write and Edit a Review")),
)

class BookDetailSlotsTest : DescribeSpec({

    describe("slot registries") {

        it("#93 TC-39: registries are empty typed lists") {
            val lists: List<List<BookDetailSection>> = listOf(
                stageAboveTitle, stageStatusStart, stageStatusEnd, stageProgress, stageCoverOverlay,
                topBarActions, overviewSections, reviewsSections, highlightsSections, filesSections,
                actionBar, screenOverlays,
            )
            lists.size shouldBe 12
            lists.forEach { it.shouldBeEmpty() }
        }

        it("#93 TC-40: Registered aggregates the registries") {
            val registered = BookDetailSlots.Registered
            registered.stageAboveTitle shouldBeSameInstanceAs stageAboveTitle
            registered.stageStatusStart shouldBeSameInstanceAs stageStatusStart
            registered.stageStatusEnd shouldBeSameInstanceAs stageStatusEnd
            registered.stageProgress shouldBeSameInstanceAs stageProgress
            registered.stageCoverOverlay shouldBeSameInstanceAs stageCoverOverlay
            registered.topBarActions shouldBeSameInstanceAs topBarActions
            registered.overviewSections shouldBeSameInstanceAs overviewSections
            registered.reviewsSections shouldBeSameInstanceAs reviewsSections
            registered.highlightsSections shouldBeSameInstanceAs highlightsSections
            registered.filesSections shouldBeSameInstanceAs filesSections
            registered.actionBar shouldBeSameInstanceAs actionBar
            registered.screenOverlays shouldBeSameInstanceAs screenOverlays

            val fake = BookDetailSection { _, _ -> }
            val slots = BookDetailSlots(actionBar = listOf(fake))
            slots.actionBar shouldBe listOf(fake)
            slots.stageAboveTitle.shouldBeEmpty()
            slots.screenOverlays.shouldBeEmpty()
        }

        it("#93 TC-41: slot files hold the seeded blocks in order") {
            val slotsDir = File(System.getProperty("user.dir"), "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots")
            for (registry in registries) {
                val lines = File(slotsDir, "${registry.file}.kt").readLines()
                val start = lines.indexOfFirst {
                    it == "internal val ${registry.name}: List<BookDetailSection> = listOfNotNull("
                }
                check(start >= 0) { "${registry.name} is not declared in ${registry.file}.kt" }

                val body = lines.drop(start + 1).takeWhile { it.trim() != ")" }.map { it.trim() }
                val expected = registry.tickets.flatMap { listOf("// $it", "null,") }
                body shouldBe expected
            }
        }
    }
})
