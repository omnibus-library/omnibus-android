package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightDeleteResult
import com.omnibus.omnibus.data.highlights.HighlightError
import com.omnibus.omnibus.data.highlights.HighlightWriteResult
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
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelHighlightDeleteTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("deleting a highlight from its menu") {
        context("on a highlight with no note") {
            it("#300 TC-6: closes the menu, for a note that is null, empty or blank") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { note ->
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi, note = note), highlights = deletingHighlights())
                    vm.tap(hitOn(cfi))

                    vm.onHighlightDeleteRequested()

                    vm.uiState.value.highlightMenu.shouldBeNull()
                }
            }

            it("#300 TC-6: deletes the highlight, for a note that is null, empty or blank") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { note ->
                    val cfi = randomCfi()
                    val row = savedAt(cfi, note = note)
                    val highlights = deletingHighlights()
                    val vm = savedVm(row, highlights = highlights)
                    vm.tap(hitOn(cfi))

                    vm.onHighlightDeleteRequested()

                    highlights.deletes shouldBe listOf(row.id)
                }
            }

            it("#300 TC-6: opens no confirmation, for a note that is null, empty or blank") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { note ->
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi, note = note), highlights = deletingHighlights())
                    vm.tap(hitOn(cfi))

                    vm.onHighlightDeleteRequested()

                    vm.uiState.value.highlightDeleteConfirmation.shouldBeNull()
                }
            }
        }

        context("on a highlight with a note") {
            it("#300 TC-8: closes the menu") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, note = randomString()), highlights = deletingHighlights())
                vm.tap(hitOn(cfi))

                vm.onHighlightDeleteRequested()

                vm.uiState.value.highlightMenu.shouldBeNull()
            }

            it("#300 TC-8: asks first, for the highlight's range") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, note = randomString()), highlights = deletingHighlights())
                vm.tap(hitOn(cfi))

                vm.onHighlightDeleteRequested()

                vm.uiState.value.highlightDeleteConfirmation shouldBe HighlightDeleteConfirmation(cfi)
            }

            it("#300 TC-8: deletes nothing until the reader confirms") {
                val cfi = randomCfi()
                val highlights = deletingHighlights()
                val vm = savedVm(savedAt(cfi, note = randomString()), highlights = highlights)
                vm.tap(hitOn(cfi))

                vm.onHighlightDeleteRequested()

                highlights.deletes.shouldBeEmpty()
            }
        }

        context("when the server confirms") {
            context("on a saved highlight") {
                it("#300 TC-7: the highlight leaves the page once the server confirms") {
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi, note = null), highlights = deletingHighlights())
                    vm.tap(hitOn(cfi))

                    vm.onHighlightDeleteRequested()

                    vm.uiState.value.pageHighlights.map { it.cfiRange.trim() } shouldBe emptyList()
                }
            }

            context("on a highlight made in this session") {
                it("#300 TC-7: the highlight leaves the page once the server confirms") {
                    val uuid = randomString()
                    val cfi = randomCfi()
                    val savedId = randomLong()
                    val highlights = deletingHighlights().apply {
                        seed(uuid, HighlightsState.Loaded(emptyList()))
                        onAdd = { draft ->
                            HighlightWriteResult.Saved(randomHighlight().copy(id = savedId, location = draft.cfiRange))
                        }
                    }
                    val vm = sut(bookUuid = uuid, highlightsRepository = highlights)
                    vm.selectText(cfi)
                    vm.onHighlightColorChosen(randomEnum())
                    vm.tap(hitOn(cfi))

                    vm.onHighlightDeleteRequested()

                    vm.uiState.value.pageHighlights.map { it.cfiRange.trim() } shouldBe emptyList()
                }
            }
        }

        context("with several highlights under the tap") {
            it("#300 TC-10: deletes only the active highlight") {
                val cfis = List(3) { randomCfi() }
                val rows = cfis.map { savedAt(it, note = null) }
                val highlights = deletingHighlights()
                val vm = savedVm(*rows.toTypedArray(), highlights = highlights)
                vm.tap(*cfis.map { hitOn(it) }.toTypedArray())
                vm.onHighlightMenuStepped(1)

                vm.onHighlightDeleteRequested()

                highlights.deletes shouldBe listOf(rows[1].id)
            }

            it("#300 TC-10: keeps the other highlights drawn") {
                val cfis = List(3) { randomCfi() }
                val rows = cfis.map { savedAt(it, note = null) }
                val vm = savedVm(*rows.toTypedArray(), highlights = deletingHighlights())
                vm.tap(*cfis.map { hitOn(it) }.toTypedArray())
                vm.onHighlightMenuStepped(1)

                vm.onHighlightDeleteRequested()

                vm.uiState.value.pageHighlights.map { it.cfiRange.trim() } shouldBe listOf(cfis[0], cfis[2])
            }
        }

        context("when the delete fails") {
            it("#300 TC-11: raises the delete notice, for every kind of failure") {
                checkAll(Exhaustive.collection(allHighlightErrors())) { error ->
                    val cfi = randomCfi()
                    val highlights = TestHighlightsRepository().apply {
                        onDelete = { _, _ -> HighlightDeleteResult.Failed(error) }
                    }
                    val vm = savedVm(savedAt(cfi, note = null), highlights = highlights)
                    vm.tap(hitOn(cfi))

                    vm.onHighlightDeleteRequested()

                    vm.uiState.value.notice shouldBe ReaderNotice.HighlightDeleteFailed
                }
            }

            it("#300 TC-11: keeps the highlight drawn, for every kind of failure") {
                checkAll(Exhaustive.collection(allHighlightErrors())) { error ->
                    val cfi = randomCfi()
                    val highlights = TestHighlightsRepository().apply {
                        onDelete = { _, _ -> HighlightDeleteResult.Failed(error) }
                    }
                    val vm = savedVm(savedAt(cfi, note = null), highlights = highlights)
                    vm.tap(hitOn(cfi))

                    vm.onHighlightDeleteRequested()

                    vm.uiState.value.pageHighlights.map { it.cfiRange.trim() } shouldBe listOf(cfi)
                }
            }
        }
    }

    describe("answering the note dialog") {
        context("when the dialog is cancelled") {
            it("#300 TC-9: keeps the highlight") {
                val cfi = randomCfi()
                val highlights = deletingHighlights()
                val vm = openedNoteDialog(cfi, highlights)

                vm.onHighlightDeleteCancelled()

                highlights.deletes.shouldBeEmpty()
            }

            it("#300 TC-9: clears the dialog") {
                val cfi = randomCfi()
                val vm = openedNoteDialog(cfi, deletingHighlights())

                vm.onHighlightDeleteCancelled()

                vm.uiState.value.highlightDeleteConfirmation.shouldBeNull()
            }
        }

        context("when the delete is confirmed") {
            it("#300 TC-9: deletes the highlight") {
                val cfi = randomCfi()
                val row = savedAt(cfi, note = randomString())
                val highlights = deletingHighlights()
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))
                vm.onHighlightDeleteRequested()

                vm.onHighlightDeleteConfirmed()

                highlights.deletes shouldBe listOf(row.id)
            }

            it("#300 TC-9: clears the dialog") {
                val cfi = randomCfi()
                val vm = openedNoteDialog(cfi, deletingHighlights())

                vm.onHighlightDeleteConfirmed()

                vm.uiState.value.highlightDeleteConfirmation.shouldBeNull()
            }
        }
    }

    describe("deleting a highlight whose save is in flight") {
        context("when the save succeeds") {
            it("#300 TC-12: does not delete before the save answers") {
                val gate = CompletableDeferred<Unit>()
                val highlights = savingHighlights(gate, savedId = randomLong(), saves = true)
                val vm = sessionVm(highlights)
                val cfi = randomCfi()
                vm.selectText(cfi)
                vm.onHighlightColorChosen(randomEnum())
                vm.tap(hitOn(cfi))
                vm.onHighlightDeleteRequested()

                highlights.deletes.shouldBeEmpty()
            }

            it("#300 TC-12: deletes the saved row once the save answers") {
                val gate = CompletableDeferred<Unit>()
                val savedId = randomLong()
                val highlights = savingHighlights(gate, savedId = savedId, saves = true)
                val vm = sessionVm(highlights)
                val cfi = randomCfi()
                vm.selectText(cfi)
                vm.onHighlightColorChosen(randomEnum())
                vm.tap(hitOn(cfi))
                vm.onHighlightDeleteRequested()

                gate.complete(Unit)

                highlights.deletes shouldBe listOf(savedId)
            }
        }

        context("when the save fails") {
            it("#300 TC-12: does not delete the highlight") {
                val gate = CompletableDeferred<Unit>()
                val highlights = savingHighlights(gate, savedId = randomLong(), saves = false)
                val vm = sessionVm(highlights)
                val cfi = randomCfi()
                vm.selectText(cfi)
                vm.onHighlightColorChosen(randomEnum())
                vm.tap(hitOn(cfi))
                vm.onHighlightDeleteRequested()

                gate.complete(Unit)

                highlights.deletes.shouldBeEmpty()
            }

            it("#300 TC-12: raises the save notice, not the delete one") {
                val gate = CompletableDeferred<Unit>()
                val highlights = savingHighlights(gate, savedId = randomLong(), saves = false)
                val vm = sessionVm(highlights)
                val cfi = randomCfi()
                vm.selectText(cfi)
                vm.onHighlightColorChosen(randomEnum())
                vm.tap(hitOn(cfi))
                vm.onHighlightDeleteRequested()

                gate.complete(Unit)

                vm.uiState.value.notice shouldBe ReaderNotice.HighlightSaveFailed
            }
        }
    }
})

/** Every way a delete can fail, so a failure test covers each one. */
private fun allHighlightErrors(): List<HighlightError> = listOf(
    HighlightError.NoServer,
    HighlightError.Unreachable,
    HighlightError.SessionExpired,
    HighlightError.BookNotFound,
    HighlightError.Rejected,
    HighlightError.Http(500),
    HighlightError.UnexpectedResponse,
)

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

/** A ViewModel for a book with no saved highlights, whose session highlights are saved by [highlights]. */
private fun sessionVm(highlights: TestHighlightsRepository): ReaderViewModel {
    val uuid = randomString()
    highlights.seed(uuid, HighlightsState.Loaded(emptyList()))
    return sut(bookUuid = uuid, highlightsRepository = highlights)
}

/**
 * A repository whose create answers after [gate] opens: with a saved row of [savedId] when [saves], else a failure.
 * Deletes succeed.
 */
private fun savingHighlights(gate: CompletableDeferred<Unit>, savedId: Long, saves: Boolean): TestHighlightsRepository =
    deletingHighlights().apply {
        onAdd = { draft ->
            gate.await()
            if (saves) {
                HighlightWriteResult.Saved(randomHighlight().copy(id = savedId, location = draft.cfiRange))
            } else {
                HighlightWriteResult.Failed(HighlightError.Unreachable)
            }
        }
    }

/** A page tap that landed on [hits]: the first at the top level, as the glue reports it, and every hit under `hits`. */
private fun ReaderViewModel.tap(vararg hits: AnnotationHit) {
    val first = hits.first()
    val payload = AnnotationTapData(cfiRange = first.cfiRange, rects = first.rects, hits = hits.toList())
    onPageEvent(ReaderEvent.AnnotationTap(payload))
}

/** A hit on [cfi] with one random rect. */
private fun hitOn(cfi: String): AnnotationHit = AnnotationHit(cfi, listOf(randomPageRect()))

/** A live selection over [cfi] with [text]. */
private fun ReaderViewModel.selectText(cfi: String, text: String = randomString()) =
    onPageEvent(ReaderEvent.Selection(randomSelectionData().copy(cfiRange = cfi, text = text)))

/** A ViewModel with a saved, noted highlight at [cfi], its menu open and its note dialog up. */
private fun openedNoteDialog(cfi: String, highlights: TestHighlightsRepository): ReaderViewModel {
    val vm = savedVm(savedAt(cfi, note = randomString()), highlights = highlights)
    vm.tap(hitOn(cfi))
    vm.onHighlightDeleteRequested()
    return vm
}
