package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomPageRect
import com.omnibus.omnibus.feature.reader.ui.randomSelectionData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class ReaderUIStateSelectionTest : DescribeSpec({

    describe("whether the highlight menu shows") {
        it("#54 TC-23: shows only for a settled selection with no handle held, chrome hidden and no sheet") {
            checkAll(
                Exhaustive.boolean(),
                Exhaustive.boolean(),
                Exhaustive.boolean(),
                Exhaustive.boolean(),
                Exhaustive.boolean(),
                Exhaustive.of<ReaderSheet?>(null, ReaderSheet.Contents, ReaderSheet.Settings),
            ) { dragging, adjusting, noRects, noRange, chromeVisible, sheet ->
                val selection = randomSelectionData().copy(
                    dragging = dragging,
                    rects = if (noRects) emptyList() else listOf(randomPageRect()),
                    cfiRange = if (noRange) null else randomCfi(),
                )
                val state = ReaderUIState(
                    selection = selection,
                    adjustingSelection = adjusting,
                    chromeVisible = chromeVisible,
                    sheet = sheet,
                )

                val settled = !dragging && !adjusting && !noRects && !noRange && !chromeVisible && sheet == null
                state.selectionMenuShown shouldBe settled
            }
        }

        context("without a selection") {
            it("#54 TC-23: is not shown") {
                ReaderUIState(selection = null).selectionMenuShown shouldBe false
            }
        }
    }
})
