package com.omnibus.omnibus.feature.bookdetail.more.merge

import com.omnibus.omnibus.feature.bookdetail.more.moreMenuEntries
import com.omnibus.omnibus.feature.bookdetail.more.parseRegistry
import com.omnibus.omnibus.feature.bookdetail.wishlist.WishlistMenuEntry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

class MergeActivationTest : DescribeSpec({

    describe("merge menu entry") {

        val source = File(
            System.getProperty("user.dir"),
            "src/main/java/com/omnibus/omnibus/feature/bookdetail/more/MoreMenuEntries.kt",
        ).readLines()

        it("#120 TC-9: the More menu registers the merge entry after the wishlist entry, by qualified name") {
            moreMenuEntries shouldContain MergeMenuEntry
            moreMenuEntries.indexOf(MergeMenuEntry) shouldBeGreaterThan moreMenuEntries.indexOf(WishlistMenuEntry)

            val block = requireNotNull(parseRegistry(source)) { "registry body did not parse" }
                .single { it.comment == "// #120 Merge with" }
            block.value shouldBe "com.omnibus.omnibus.feature.bookdetail.more.merge.MergeMenuEntry,"
            source.none { it.startsWith("import com.omnibus.omnibus.feature.bookdetail.more.merge") } shouldBe true
        }
    }
})
