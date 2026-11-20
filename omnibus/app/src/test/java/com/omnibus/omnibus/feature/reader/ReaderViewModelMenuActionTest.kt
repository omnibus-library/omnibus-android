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
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelMenuActionTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("choosing Copy in the highlight menu") {
        context("with a highlight menu open") {
            it("#329 TC-5: closes the menu") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, note = null), highlights = deletingHighlights())
                vm.tap(hitOn(cfi))

                vm.onHighlightMenuAction(HighlightMenuAction.Copy)

                vm.uiState.value.highlightMenu.shouldBeNull()
            }

            it("#329 TC-5: deletes nothing") {
                val cfi = randomCfi()
                val highlights = deletingHighlights()
                val vm = savedVm(savedAt(cfi, note = null), highlights = highlights)
                vm.tap(hitOn(cfi))

                vm.onHighlightMenuAction(HighlightMenuAction.Copy)

                highlights.deletes.shouldBeEmpty()
            }
        }
    }

    describe("choosing Delete in the highlight menu") {
        context("on a highlight with no note") {
            it("#329 TC-6: closes the menu, for a note that is null, empty or blank") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { note ->
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi, note = note), highlights = deletingHighlights())
                    vm.tap(hitOn(cfi))

                    vm.onHighlightMenuAction(HighlightMenuAction.Delete)

                    vm.uiState.value.highlightMenu.shouldBeNull()
                }
            }

            it("#329 TC-6: deletes the highlight at once, for a note that is null, empty or blank") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { note ->
                    val cfi = randomCfi()
                    val row = savedAt(cfi, note = note)
                    val highlights = deletingHighlights()
                    val vm = savedVm(row, highlights = highlights)
                    vm.tap(hitOn(cfi))

                    vm.onHighlightMenuAction(HighlightMenuAction.Delete)

                    highlights.deletes shouldBe listOf(row.id)
                }
            }

            it("#329 TC-6: opens no confirmation, for a note that is null, empty or blank") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { note ->
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi, note = note), highlights = deletingHighlights())
                    vm.tap(hitOn(cfi))

                    vm.onHighlightMenuAction(HighlightMenuAction.Delete)

                    vm.uiState.value.highlightDeleteConfirmation.shouldBeNull()
                }
            }
        }

        context("on a highlight with a note") {
            it("#329 TC-7: closes the menu") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, note = randomString()), highlights = deletingHighlights())
                vm.tap(hitOn(cfi))

                vm.onHighlightMenuAction(HighlightMenuAction.Delete)

                vm.uiState.value.highlightMenu.shouldBeNull()
            }

            it("#329 TC-7: asks first, for the highlight's range") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, note = randomString()), highlights = deletingHighlights())
                vm.tap(hitOn(cfi))

                vm.onHighlightMenuAction(HighlightMenuAction.Delete)

                vm.uiState.value.highlightDeleteConfirmation shouldBe HighlightDeleteConfirmation(cfi)
            }

            it("#329 TC-7: deletes nothing until the reader confirms") {
                val cfi = randomCfi()
                val highlights = deletingHighlights()
                val vm = savedVm(savedAt(cfi, note = randomString()), highlights = highlights)
                vm.tap(hitOn(cfi))

                vm.onHighlightMenuAction(HighlightMenuAction.Delete)

                highlights.deletes.shouldBeEmpty()
            }
        }
    }

    describe("choosing Copy in the selection menu") {
        context("with a selection up") {
            it("#329 TC-8: clears the selection") {
                val vm = sut()
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                vm.onSelectionMenuAction(HighlightMenuAction.Copy)

                vm.uiState.value.selection.shouldBeNull()
            }
        }
    }

    describe("choosing Delete in the selection menu") {
        context("with a selection up") {
            it("#329 TC-9: leaves the selection up") {
                val vm = sut()
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))
                val before = vm.uiState.value

                vm.onSelectionMenuAction(HighlightMenuAction.Delete)

                vm.uiState.value shouldBe before
            }

            it("#329 TC-9: saves no highlight") {
                val highlights = deletingHighlights()
                val vm = sut(highlightsRepository = highlights)
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                vm.onSelectionMenuAction(HighlightMenuAction.Delete)

                highlights.drafts.shouldBeEmpty()
            }

            it("#329 TC-9: deletes no highlight") {
                val highlights = deletingHighlights()
                val vm = sut(highlightsRepository = highlights)
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                vm.onSelectionMenuAction(HighlightMenuAction.Delete)

                highlights.deletes.shouldBeEmpty()
            }
        }
    }
})

/** A saved highlight at [cfi], with a random colour, text and [note]. */
private fun savedAt(cfi: String, note: String?): Highlight =
    randomHighlight().copy(location = cfi, color = randomEnum(), text = randomString(), note = note)

/** A repository whose delete succeeds, so a test can read the ids it was asked to delete. */
private fun deletingHighlights(): TestHighlightsRepository = TestHighlightsRepository().apply {
    onDelete = { _, _ -> HighlightDeleteResult.Deleted }
}

/** A ViewModel for a book whose highlights read holds [rows]. [highlights] lets a test script the writes too. */
private fun savedVm(
    vararg rows: Highlight,
    highlights: TestHighlightsRepository = TestHighlightsRepository(),
): ReaderViewModel {
    val uuid = randomString()
    highlights.seed(uuid, HighlightsState.Loaded(rows.toList()))
    return sut(bookUuid = uuid, highlightsRepository = highlights)
}

/** A page tap that landed on [hits]: the first at the top level, as the glue reports it, and every hit under `hits`. */
private fun ReaderViewModel.tap(vararg hits: AnnotationHit) {
    val first = hits.first()
    val payload = AnnotationTapData(cfiRange = first.cfiRange, rects = first.rects, hits = hits.toList())
    onPageEvent(ReaderEvent.AnnotationTap(payload))
}

/** A hit on [cfi] with one random rect. */
private fun hitOn(cfi: String): AnnotationHit = AnnotationHit(cfi, listOf(randomPageRect()))
