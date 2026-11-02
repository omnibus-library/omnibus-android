package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.status.ReadStatusSection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class ReadStatusActivationTest : DescribeSpec({

    describe("read status slot") {

        it("TC-78: the status row's start slot registers the read status section") {
            stageStatusStart shouldBe listOf(ReadStatusSection)
            BookDetailSlots.Registered.stageStatusStart shouldContain ReadStatusSection
        }
    }
})
