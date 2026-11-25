package com.omnibus.omnibus.feature.series

import com.omnibus.omnibus.feature.series.ui.SeriesContentMaxWidth
import com.omnibus.omnibus.feature.series.ui.seriesContentMaxWidth
import com.omnibus.omnibus.feature.shell.RailBreakpoint
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import androidx.compose.ui.unit.dp

class SeriesContentWidthTest : DescribeSpec({

    describe("the width of the Series Details column") {

        it("#349 TC-19: has no cap for any window narrower than the rail's breakpoint") {
            checkAll(Arb.int(0..599)) { width ->
                seriesContentMaxWidth(width.dp) shouldBe null
            }
        }

        it("#349 TC-19: caps the column at 560dp for any window at or wider than the rail's breakpoint") {
            checkAll(Arb.int(600..5000)) { width ->
                seriesContentMaxWidth(width.dp) shouldBe SeriesContentMaxWidth
            }
        }

        it("#349 TC-19: changes exactly at the rail's breakpoint, 600dp") {
            seriesContentMaxWidth(RailBreakpoint - 0.01f.dp) shouldBe null
            seriesContentMaxWidth(RailBreakpoint) shouldBe SeriesContentMaxWidth
        }
    }
})
