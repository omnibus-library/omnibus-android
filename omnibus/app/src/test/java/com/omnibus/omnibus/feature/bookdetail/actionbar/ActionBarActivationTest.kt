package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.actionBar
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class ActionBarActivationTest : DescribeSpec({

    describe("action bar slot") {

        it("TC-31: the action bar slot registers the action bar section") {
            actionBar shouldBe listOf(BookActionBarSection)
        }

        it("TC-32: the registered slots contain the action bar section") {
            BookDetailSlots.Registered.actionBar shouldContain BookActionBarSection
        }
    }
})
