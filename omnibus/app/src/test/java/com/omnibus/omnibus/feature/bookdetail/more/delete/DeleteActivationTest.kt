package com.omnibus.omnibus.feature.bookdetail.more.delete

import com.omnibus.omnibus.feature.bookdetail.more.moreMenuEntries
import com.omnibus.omnibus.feature.bookdetail.more.parseRegistry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File

class DeleteActivationTest : DescribeSpec({

    val source = File(
        System.getProperty("user.dir"),
        "src/main/java/com/omnibus/omnibus/feature/bookdetail/more/MoreMenuEntries.kt",
    ).readLines()

    describe("delete menu entry") {

        it("#121 TC-17: the delete block names the entry by its qualified name, the registry keeps the other blocks, and the entry is last") {
            val blocks = requireNotNull(parseRegistry(source)) { "registry body did not parse" }

            val block = blocks.single { it.comment == "// #121 Delete" }
            block.value shouldBe "com.omnibus.omnibus.feature.bookdetail.more.delete.DeleteMenuEntry,"
            source.none { it.startsWith("import com.omnibus.omnibus.feature.bookdetail.more.delete") } shouldBe true

            // The Bookmarks, Export and Merge blocks are left out, because those tickets activate theirs independently.
            val otherTickets = setOf("// #117 Bookmarks", "// #118 Export", "// #120 Merge with", "// #121 Delete")
            blocks.filterNot { it.comment in otherTickets }.map { it.value } shouldBe listOf(
                "ShelfMenuEntry,",
                "com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistMenuEntry,",
            )

            moreMenuEntries shouldContain DeleteMenuEntry
            moreMenuEntries.last() shouldBe DeleteMenuEntry
        }
    }
})
