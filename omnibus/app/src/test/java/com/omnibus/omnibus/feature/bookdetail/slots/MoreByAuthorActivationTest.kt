package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.overview.author.MoreByAuthorSection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class MoreByAuthorActivationTest : DescribeSpec({

    describe("more by this author slot") {

        it("#105 TC-27: the Overview list registers the section once, after the series and before read-alikes") {
            overviewSections.count { it === MoreByAuthorSection } shouldBe 1
            BookDetailSlots.Registered.overviewSections shouldContain MoreByAuthorSection
        }
    }
})
