package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class PlayerLayoutTest : DescribeSpec({

    describe("the cover's height") {
        // AND-UI-5, P-SCR-5
        it("#165 TC-40: is never negative, for an empty or negative band or width") {
            checkAll(Exhaustive.of(0f to 300f, -10f to 300f, 500f to -5f)) { (band, width) ->
                coverHeightDp(bandHeightDp = band, widthDp = width) shouldBeExactly 0f
            }
        }
    }
})
