package com.omnibus.omnibus.feature.search

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class SearchLayoutTest : DescribeSpec({

    describe("the centred column") {

        it("#351 TC-22: leaves no space at the sides of a window up to 560dp wide") {
            checkAll(Exhaustive.of(320.dp, 411.dp, 560.dp)) { width ->
                searchColumnSide(width) shouldBe 0.dp
            }
        }

        it("#351 TC-22: centres a column of 560dp in a window wider than that") {
            checkAll(Exhaustive.of(600.dp, 840.dp, 1200.dp)) { width ->
                searchColumnSide(width) shouldBe (width - 560.dp) / 2
            }
        }
    }
})
