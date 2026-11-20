package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightDeleteResult
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.feature.reader.ui.AnnotationHit
import com.omnibus.omnibus.feature.reader.ui.AnnotationTapData
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomPageRect
import com.omnibus.omnibus.feature.reader.ui.randomSelectionData
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelHighlightShareTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("choosing Share in the highlight menu") {
        context("with a highlight menu open") {
            it("#330 TC-5: closes the menu") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi))
                vm.tap(hitOn(cfi))
                vm.uiState.value.highlightMenu.shouldNotBeNull()

                vm.onHighlightMenuAction(HighlightMenuAction.Share)

                vm.uiState.value.highlightMenu.shouldBeNull()
            }

            it("#330 TC-5: deletes nothing") {
                val cfi = randomCfi()
                // A delete would succeed, so a wrong one shows as a recorded id, not an unscripted call.
                val highlights = TestHighlightsRepository().apply { onDelete = { _, _ -> HighlightDeleteResult.Deleted } }
                val vm = savedVm(savedAt(cfi), highlights = highlights)
                vm.tap(hitOn(cfi))

                vm.onHighlightMenuAction(HighlightMenuAction.Share)

                highlights.deletes.shouldBeEmpty()
            }
        }
    }

    describe("choosing Share in the selection menu") {
        context("with a selection up") {
            it("#330 TC-6: clears the selection") {
                val vm = sut()
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))
                vm.uiState.value.selection.shouldNotBeNull()

                vm.onSelectionMenuAction(HighlightMenuAction.Share)

                vm.uiState.value.selection.shouldBeNull()
            }

            it("#330 TC-7: saves no highlight") {
                val highlights = TestHighlightsRepository()
                val vm = sut(highlightsRepository = highlights)
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                vm.onSelectionMenuAction(HighlightMenuAction.Share)

                highlights.drafts.shouldBeEmpty()
            }
        }
    }
})

/** A saved highlight at [cfi], with a random colour and text. */
private fun savedAt(cfi: String): Highlight =
    randomHighlight().copy(location = cfi, color = randomEnum(), text = randomString())

/** A ViewModel for a book whose highlights read holds [rows]. */
private fun savedVm(
    vararg rows: Highlight,
    highlights: TestHighlightsRepository = TestHighlightsRepository(),
): ReaderViewModel {
    val uuid = randomString()
    highlights.seed(uuid, HighlightsState.Loaded(rows.toList()))
    return sut(bookUuid = uuid, highlightsRepository = highlights)
}

/** A page tap that landed on [hit], as the glue reports it. */
private fun ReaderViewModel.tap(hit: AnnotationHit) {
    onPageEvent(ReaderEvent.AnnotationTap(AnnotationTapData(cfiRange = hit.cfiRange, rects = hit.rects, hits = listOf(hit))))
}

/** A hit on [cfi] with one random rect. */
private fun hitOn(cfi: String): AnnotationHit = AnnotationHit(cfi, listOf(randomPageRect()))
