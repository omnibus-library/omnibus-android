package com.omnibus.omnibus.feature.bookdetail.slots

import com.omnibus.omnibus.feature.bookdetail.progress.ReadingProgressSection
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class ReadingProgressActivationTest : DescribeSpec({

    describe("reading progress slot") {

        it("TC-78: the progress slot registers the progress section") {
            stageProgress shouldBe listOf(ReadingProgressSection)
            BookDetailSlots.Registered.stageProgress shouldContain ReadingProgressSection
        }
    }
})
