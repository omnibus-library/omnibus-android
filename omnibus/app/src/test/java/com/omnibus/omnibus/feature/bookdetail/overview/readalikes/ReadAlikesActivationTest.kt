package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs

class ReadAlikesActivationTest : DescribeSpec({
    describe("the place of the read-alikes section on the Overview tab") {
        it("#106 TC-53: comes last") {
            BookDetailSlots.Registered.overviewSections.last() shouldBeSameInstanceAs ReadAlikesSection
        }
    }
})
