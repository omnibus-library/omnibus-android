package com.omnibus.omnibus.feature.bookdetail.more

import com.omnibus.omnibus.feature.bookdetail.slots.topBarActions
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain

class MoreTopBarSlotTest : DescribeSpec({

    describe("the top bar slots") {
        it("#116 TC-64: hold the More button") {
            topBarActions shouldContain BookMoreButton
        }
    }
})
