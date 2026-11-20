package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightError
import com.omnibus.omnibus.data.highlights.HighlightRecolorResult
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
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelHighlightRecolorTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("recolouring a highlight from its menu") {
        context("on a saved highlight") {
            it("#301 TC-8: closes the menu") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val vm = savedVm(row, highlights = recolourHeldBy(CompletableDeferred()))
                vm.tap(hitOn(cfi))

                vm.onHighlightRecolorChosen(otherColourThan(row.color))

                vm.uiState.value.highlightMenu.shouldBeNull()
            }

            it("#301 TC-8: repaints the highlight before the server answers") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val chosen = otherColourThan(row.color)
                val vm = savedVm(row, highlights = recolourHeldBy(CompletableDeferred()))
                vm.tap(hitOn(cfi))

                vm.onHighlightRecolorChosen(chosen)

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe chosen
            }

            it("#301 TC-8: sends the recolour for the saved id") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val chosen = otherColourThan(row.color)
                val highlights = recolourHeldBy(CompletableDeferred())
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))

                vm.onHighlightRecolorChosen(chosen)

                highlights.recolors shouldBe listOf(row.id to chosen)
            }

            context("with the checked colour") {
                it("#301 TC-10: closes the menu") {
                    val cfi = randomCfi()
                    val row = savedAt(cfi, randomEnum())
                    val vm = savedVm(row, highlights = recolourHeldBy(CompletableDeferred()))
                    vm.tap(hitOn(cfi))

                    vm.onHighlightRecolorChosen(row.color)

                    vm.uiState.value.highlightMenu.shouldBeNull()
                }

                it("#301 TC-10: sends no recolour") {
                    val cfi = randomCfi()
                    val row = savedAt(cfi, randomEnum())
                    val highlights = recolourHeldBy(CompletableDeferred())
                    val vm = savedVm(row, highlights = highlights)
                    vm.tap(hitOn(cfi))

                    vm.onHighlightRecolorChosen(row.color)

                    highlights.recolors.shouldBeEmpty()
                }

                it("#301 TC-10: leaves the page as it was") {
                    val cfi = randomCfi()
                    val row = savedAt(cfi, randomEnum())
                    val vm = savedVm(row, highlights = recolourHeldBy(CompletableDeferred()))
                    vm.tap(hitOn(cfi))
                    val before = vm.uiState.value.pageHighlights

                    vm.onHighlightRecolorChosen(row.color)

                    vm.uiState.value.pageHighlights shouldBe before
                }
            }

            context("with several highlights under the tap") {
                it("#301 TC-11: recolours only the active highlight") {
                    val cfis = List(3) { randomCfi() }
                    val rows = cfis.map { savedAt(it, randomEnum()) }
                    val highlights = recolourHeldBy(CompletableDeferred())
                    val vm = savedVm(*rows.toTypedArray(), highlights = highlights)
                    vm.tap(*cfis.map { hitOn(it) }.toTypedArray())
                    vm.onHighlightMenuStepped(1)
                    val activeCfi = vm.uiState.value.highlightMenu.shouldNotBeNull().activeEntry.cfiRange
                    val activeRow = rows.first { it.location?.trim() == activeCfi }
                    val chosen = HighlightColor.entries.first { colour -> rows.none { it.color == colour } }

                    vm.onHighlightRecolorChosen(chosen)

                    highlights.recolors shouldBe listOf(activeRow.id to chosen)
                    vm.uiState.value.pageHighlights.map { it.cfiRange.trim() to it.color } shouldBe
                        cfis.zip(rows) { cfi, row -> cfi to (if (row.id == activeRow.id) chosen else row.color) }
                }
            }

            context("before the server answers") {
                it("#301 TC-12: reopening the menu shows the new colour") {
                    val cfi = randomCfi()
                    val row = savedAt(cfi, randomEnum())
                    val chosen = otherColourThan(row.color)
                    val vm = savedVm(row, highlights = recolourHeldBy(CompletableDeferred()))
                    vm.tap(hitOn(cfi))
                    vm.onHighlightRecolorChosen(chosen)

                    vm.tap(hitOn(cfi))

                    vm.uiState.value.highlightMenu.shouldNotBeNull().activeEntry.color shouldBe chosen
                }
            }

            context("after the server answers") {
                it("#301 TC-12: reopening the menu shows the new colour") {
                    val cfi = randomCfi()
                    val row = savedAt(cfi, randomEnum())
                    val chosen = otherColourThan(row.color)
                    val gate = CompletableDeferred<Unit>()
                    val vm = savedVm(row, highlights = recolourHeldBy(gate))
                    vm.tap(hitOn(cfi))
                    vm.onHighlightRecolorChosen(chosen)
                    gate.complete(Unit)

                    vm.tap(hitOn(cfi))

                    vm.uiState.value.highlightMenu.shouldNotBeNull().activeEntry.color shouldBe chosen
                }
            }

            context("when the recolour fails") {
                it("#301 TC-14: puts the previous colour back, for every kind of failure") {
                    checkAll(Exhaustive.collection(allHighlightErrors())) { error ->
                        val cfi = randomCfi()
                        val row = savedAt(cfi, randomEnum())
                        val highlights = TestHighlightsRepository().apply {
                            onRecolor = { _, _, _ -> HighlightRecolorResult.Failed(error) }
                        }
                        val vm = savedVm(row, highlights = highlights)
                        vm.tap(hitOn(cfi))

                        vm.onHighlightRecolorChosen(otherColourThan(row.color))

                        vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe row.color
                    }
                }

                it("#301 TC-14: raises the recolour notice, for every kind of failure") {
                    checkAll(Exhaustive.collection(allHighlightErrors())) { error ->
                        val cfi = randomCfi()
                        val row = savedAt(cfi, randomEnum())
                        val highlights = TestHighlightsRepository().apply {
                            onRecolor = { _, _, _ -> HighlightRecolorResult.Failed(error) }
                        }
                        val vm = savedVm(row, highlights = highlights)
                        vm.tap(hitOn(cfi))

                        vm.onHighlightRecolorChosen(otherColourThan(row.color))

                        vm.uiState.value.notice shouldBe ReaderNotice.HighlightRecolorFailed
                    }
                }
            }
        }
    }

    describe("recolouring a highlight when the server confirms") {
        context("on a saved highlight") {
            it("#301 TC-13: keeps the new colour once the server confirms") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val chosen = otherColourThan(row.color)
                val gate = CompletableDeferred<Unit>()
                val vm = savedVm(row, highlights = recolourHeldBy(gate))
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(chosen)

                gate.complete(Unit)

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe chosen
            }

            it("#301 TC-13: never draws the old colour again") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val gate = CompletableDeferred<Unit>()
                val vm = savedVm(row, highlights = recolourHeldBy(gate))
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(otherColourThan(row.color))

                val drawn = coloursDrawnAt(vm, cfi) { gate.complete(Unit) }

                drawn.shouldNotBeEmpty()
                drawn.shouldNotContain(row.color)
            }
        }

        context("on a highlight made in this session") {
            it("#301 TC-13: keeps the new colour once the server confirms") {
                val cfi = randomCfi()
                val color: HighlightColor = randomEnum()
                val chosen = otherColourThan(color)
                val gate = CompletableDeferred<Unit>()
                val vm = sessionVm(sessionSaves(gate, savedId = randomLong()))
                vm.drawSession(cfi, color)
                vm.onHighlightRecolorChosen(chosen)

                gate.complete(Unit)

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe chosen
            }

            it("#301 TC-13: never draws the old colour again") {
                val cfi = randomCfi()
                val color: HighlightColor = randomEnum()
                val gate = CompletableDeferred<Unit>()
                val vm = sessionVm(sessionSaves(gate, savedId = randomLong()))
                vm.drawSession(cfi, color)
                vm.onHighlightRecolorChosen(otherColourThan(color))

                val drawn = coloursDrawnAt(vm, cfi) { gate.complete(Unit) }

                drawn.shouldNotBeEmpty()
                drawn.shouldNotContain(color)
            }
        }
    }

    describe("recolouring a highlight whose save is in flight") {
        context("before the save answers") {
            it("#301 TC-16: repaints the highlight at once") {
                val cfi = randomCfi()
                val color: HighlightColor = randomEnum()
                val chosen = otherColourThan(color)
                val vm = sessionVm(gatedSaves(CompletableDeferred(), savedId = randomLong(), saves = true))
                vm.drawSession(cfi, color)

                vm.onHighlightRecolorChosen(chosen)

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe chosen
            }

            it("#301 TC-16: does not recolour before the save answers") {
                val cfi = randomCfi()
                val color: HighlightColor = randomEnum()
                val highlights = gatedSaves(CompletableDeferred(), savedId = randomLong(), saves = true)
                val vm = sessionVm(highlights)
                vm.drawSession(cfi, color)

                vm.onHighlightRecolorChosen(otherColourThan(color))

                highlights.recolors.shouldBeEmpty()
            }
        }

        context("when the save succeeds") {
            it("#301 TC-16: recolours the saved row once the save answers") {
                val cfi = randomCfi()
                val color: HighlightColor = randomEnum()
                val chosen = otherColourThan(color)
                val gate = CompletableDeferred<Unit>()
                val savedId = randomLong()
                val highlights = gatedSaves(gate, savedId = savedId, saves = true)
                val vm = sessionVm(highlights)
                vm.drawSession(cfi, color)
                vm.onHighlightRecolorChosen(chosen)

                gate.complete(Unit)

                highlights.recolors shouldBe listOf(savedId to chosen)
            }
        }

        context("when the save fails") {
            it("#301 TC-16: sends nothing") {
                val cfi = randomCfi()
                val color: HighlightColor = randomEnum()
                val gate = CompletableDeferred<Unit>()
                val highlights = gatedSaves(gate, savedId = randomLong(), saves = false)
                val vm = sessionVm(highlights)
                vm.drawSession(cfi, color)
                vm.onHighlightRecolorChosen(otherColourThan(color))

                gate.complete(Unit)

                highlights.recolors.shouldBeEmpty()
            }

            it("#301 TC-16: raises the save notice") {
                val cfi = randomCfi()
                val color: HighlightColor = randomEnum()
                val gate = CompletableDeferred<Unit>()
                val vm = sessionVm(gatedSaves(gate, savedId = randomLong(), saves = false))
                vm.drawSession(cfi, color)
                vm.onHighlightRecolorChosen(otherColourThan(color))

                gate.complete(Unit)

                vm.uiState.value.notice shouldBe ReaderNotice.HighlightSaveFailed
            }
        }
    }

    describe("recolouring a highlight more than once") {
        context("when the second is chosen while the first is pending") {
            it("#301 TC-18: sends the recolours in tap order") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val first = otherColourThan(row.color)
                val second = otherColourThan(first)
                val highlights = recolourSteps(
                    CompletableDeferred<Unit>() to HighlightRecolorResult.Recolored,
                    CompletableDeferred<Unit>() to HighlightRecolorResult.Recolored,
                )
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(first)
                vm.tap(hitOn(cfi))

                vm.onHighlightRecolorChosen(second)

                highlights.recolors shouldBe listOf(row.id to first, row.id to second)
            }

            it("#301 TC-18: ends on the last colour chosen") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val first = otherColourThan(row.color)
                val second = otherColourThan(first)
                val firstGate = CompletableDeferred<Unit>()
                val secondGate = CompletableDeferred<Unit>()
                val highlights = recolourSteps(
                    firstGate to HighlightRecolorResult.Recolored,
                    secondGate to HighlightRecolorResult.Recolored,
                )
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(first)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(second)

                firstGate.complete(Unit)
                secondGate.complete(Unit)

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe second
            }
        }

        context("when the first fails while the second is pending") {
            it("#301 TC-19: an earlier failure does not revert a later pending recolour") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val first = otherColourThan(row.color)
                val second = otherColourThan(first)
                val firstGate = CompletableDeferred<Unit>()
                val secondGate = CompletableDeferred<Unit>()
                val highlights = recolourSteps(
                    firstGate to HighlightRecolorResult.Failed(HighlightError.Unreachable),
                    secondGate to HighlightRecolorResult.Recolored,
                )
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(first)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(second)

                firstGate.complete(Unit)

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe second
            }

            it("#301 TC-19: raises the recolour notice") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val first = otherColourThan(row.color)
                val second = otherColourThan(first)
                val firstGate = CompletableDeferred<Unit>()
                val secondGate = CompletableDeferred<Unit>()
                val highlights = recolourSteps(
                    firstGate to HighlightRecolorResult.Failed(HighlightError.Unreachable),
                    secondGate to HighlightRecolorResult.Recolored,
                )
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(first)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(second)

                firstGate.complete(Unit)

                vm.uiState.value.notice shouldBe ReaderNotice.HighlightRecolorFailed
            }
        }

        context("when the first succeeds and the second fails") {
            it("#301 TC-22: a failed second recolour goes back to the confirmed colour") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val first = otherColourThan(row.color)
                val second = otherColourThan(first)
                val highlights = recolourSteps(
                    answered() to HighlightRecolorResult.Recolored,
                    answered() to HighlightRecolorResult.Failed(HighlightError.Unreachable),
                )
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(first)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(second)

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe first
            }

            it("#301 TC-22: raises the recolour notice") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val first = otherColourThan(row.color)
                val second = otherColourThan(first)
                val highlights = recolourSteps(
                    answered() to HighlightRecolorResult.Recolored,
                    answered() to HighlightRecolorResult.Failed(HighlightError.Unreachable),
                )
                val vm = savedVm(row, highlights = highlights)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(first)
                vm.tap(hitOn(cfi))
                vm.onHighlightRecolorChosen(second)

                vm.uiState.value.notice shouldBe ReaderNotice.HighlightRecolorFailed
            }
        }
    }
})

/** Every way a recolour can fail, so a failure test covers each one. */
private fun allHighlightErrors(): List<HighlightError> = listOf(
    HighlightError.NoServer,
    HighlightError.Unreachable,
    HighlightError.SessionExpired,
    HighlightError.BookNotFound,
    HighlightError.Rejected,
    HighlightError.Http(404),
    HighlightError.Http(500),
    HighlightError.UnexpectedResponse,
)

/** The colour that is not [color]. A recolour to it always changes the highlight. */
private fun otherColourThan(color: HighlightColor): HighlightColor = HighlightColor.entries.first { it != color }

/** A saved highlight at [cfi] in [color], with no note. */
private fun savedAt(cfi: String, color: HighlightColor): Highlight =
    randomHighlight().copy(location = cfi, color = color, note = null)

/** A gate that is already open. */
private fun answered(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) }

/** A repository whose recolours wait for [gate], then answer [result]. */
private fun recolourHeldBy(
    gate: CompletableDeferred<Unit>,
    result: HighlightRecolorResult = HighlightRecolorResult.Recolored,
): TestHighlightsRepository = TestHighlightsRepository().apply {
    onRecolor = { _, _, _ ->
        gate.await()
        result
    }
}

/** A repository whose recolours answer in call order, each once its gate opens. */
private fun recolourSteps(
    vararg steps: Pair<CompletableDeferred<Unit>, HighlightRecolorResult>,
): TestHighlightsRepository {
    val pending = ArrayDeque(steps.toList())
    return TestHighlightsRepository().apply {
        onRecolor = { _, _, _ ->
            val (gate, result) = pending.removeFirst()
            gate.await()
            result
        }
    }
}

/**
 * A repository whose saves wait for [gate], then answer with a saved row of [savedId] or a failure. Recolours succeed at
 * once.
 */
private fun gatedSaves(gate: CompletableDeferred<Unit>, savedId: Long, saves: Boolean): TestHighlightsRepository =
    TestHighlightsRepository().apply {
        onAdd = { draft ->
            gate.await()
            if (saves) {
                HighlightWriteResult.Saved(randomHighlight().copy(id = savedId, location = draft.cfiRange))
            } else {
                HighlightWriteResult.Failed(HighlightError.Unreachable)
            }
        }
        onRecolor = { _, _, _ -> HighlightRecolorResult.Recolored }
    }

/** A repository whose saves answer at once with a saved row of [savedId], and whose recolours wait for [gate]. */
private fun sessionSaves(gate: CompletableDeferred<Unit>, savedId: Long): TestHighlightsRepository =
    recolourHeldBy(gate).apply {
        onAdd = { draft -> HighlightWriteResult.Saved(randomHighlight().copy(id = savedId, location = draft.cfiRange)) }
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

/** Every page highlight list the reader takes while [change] runs, and the colours drawn at [cfi] among them. */
private fun coloursDrawnAt(vm: ReaderViewModel, cfi: String, change: () -> Unit): List<HighlightColor> {
    val seen = mutableListOf<List<PageHighlight>>()
    val watcher = CoroutineScope(Dispatchers.Unconfined).launch { vm.uiState.collect { seen += it.pageHighlights } }
    change()
    watcher.cancel()
    return seen.flatten().filter { it.cfiRange.trim() == cfi }.map { it.color }
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

/** A session highlight at [cfi] in [color], made from a selection and left open in the highlight menu. */
private fun ReaderViewModel.drawSession(cfi: String, color: HighlightColor) {
    selectText(cfi)
    onHighlightColorChosen(color)
    tap(hitOn(cfi))
}
