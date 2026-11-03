package com.omnibus.omnibus.feature.bookdetail.more.export

import com.omnibus.omnibus.feature.bookdetail.more.moreMenuEntries
import com.omnibus.omnibus.feature.bookdetail.more.parseRegistry
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfMenuEntry
import com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistMenuEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

class ExportActivationTest : DescribeSpec({

    describe("export menu entry") {

        val source = File(
            System.getProperty("user.dir"),
            "src/main/java/com/omnibus/omnibus/feature/bookdetail/more/MoreMenuEntries.kt",
        ).readLines()

        it("#118 TC-58: the More menu registers the export entry after the shelf entry and before the wishlist entry, by qualified name") {
            moreMenuEntries shouldContain ExportMenuEntry
            moreMenuEntries.indexOf(ExportMenuEntry) shouldBeGreaterThan moreMenuEntries.indexOf(ShelfMenuEntry)
            moreMenuEntries.indexOf(WishlistMenuEntry) shouldBeGreaterThan moreMenuEntries.indexOf(ExportMenuEntry)

            val block = requireNotNull(parseRegistry(source)) { "registry body did not parse" }
                .single { it.comment == "// #118 Export" }
            block.value shouldBe "com.omnibus.omnibus.feature.bookdetail.more.export.ExportMenuEntry,"
            source.none { it.startsWith("import com.omnibus.omnibus.feature.bookdetail.more.export") } shouldBe true
        }
    }
})
