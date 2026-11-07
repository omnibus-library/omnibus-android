package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.graphics.luminance
import com.omnibus.omnibus.data.reader.ReaderTheme
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum

class ReaderChromeColorsTest : DescribeSpec({

    describe("the sync offer's Go action") {
        it("TC-116: reaches WCAG AA on the plate of every reading theme") {
            checkAll(Exhaustive.enum<ReaderTheme>()) { theme ->
                val action = readerSyncOfferActionColor(theme).luminance()
                val plate = readerChromeColors(theme).plate.luminance()

                ((maxOf(action, plate) + 0.05) / (minOf(action, plate) + 0.05)).toDouble() shouldBeGreaterThanOrEqual 4.5
            }
        }
    }
})
