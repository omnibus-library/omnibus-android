package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.feature.bookdetail.more.moreMenuEntries
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.parseRegistry
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.stageAboveTitle
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

class WishlistActivationTest : DescribeSpec({

    describe("wishlist slots") {

        it("#119 TC-39: the stage above the title registers the wishlist stage section") {
            stageAboveTitle shouldBe listOf(WishlistStageSection)
            BookDetailSlots.Registered.stageAboveTitle shouldContain WishlistStageSection
        }
    }

    describe("wishlist menu entry") {

        val source = File(
            System.getProperty("user.dir"),
            "src/main/java/com/omnibus/omnibus/feature/bookdetail/more/MoreMenuEntries.kt",
        ).readLines()

        it("#119 TC-40: the More menu registers the wishlist entry after the shelf entry, by qualified name") {
            moreMenuEntries shouldContain WishlistMenuEntry
            moreMenuEntries.indexOf(WishlistMenuEntry) shouldBeGreaterThan moreMenuEntries.indexOf(ShelfMenuEntry)

            val block = requireNotNull(parseRegistry(source)) { "registry body did not parse" }
                .single { it.comment == "// #119 Remove from wishlist" }
            block.value shouldBe "com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistMenuEntry,"
            source.none { it.startsWith("import com.omnibus.omnibus.feature.bookdetail.wishlist") } shouldBe true
        }
    }
})
