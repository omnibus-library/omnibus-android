package com.omnibus.omnibus.feature.reader

import app.cash.turbine.test
import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightError
import com.omnibus.omnibus.data.highlights.HighlightWriteResult
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.ui.AnnotationHit
import com.omnibus.omnibus.feature.reader.ui.AnnotationTapData
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadFailure
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomPageRect
import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import com.omnibus.omnibus.feature.reader.ui.randomSelectionData
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.collection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelHighlightMenuTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("opening the highlight menu on a tap") {
        context("on a saved highlight") {
            it("#299 TC-11: opens on a saved highlight with its colour and text") {
                val cfi = randomCfi()
                val row = savedAt(cfi = "  $cfi  ")
                val vm = savedVm(row)
                val hit = hitOn(cfi)

                vm.tap(hit)

                vm.uiState.value.highlightMenu shouldBe HighlightMenu(
                    listOf(
                        HighlightMenuEntry(cfi, hit.rects, row.color, row.text, hasNote = !row.note.isNullOrBlank()),
                    ),
                )
            }

            context("with a selection up") {
                it("#299 TC-14: clears a live selection") {
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi))
                    vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                    vm.tap(hitOn(cfi))

                    vm.uiState.value.selection shouldBe null
                }

                it("#299 TC-14: asks the page to clear its selection") {
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi))
                    vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                    vm.tap(hitOn(cfi))

                    vm.commands.test {
                        awaitItem() shouldBe ReaderCommand.ClearSelection
                        expectNoEvents()
                    }
                }
            }

            context("with the chrome up") {
                it("#299 TC-15: opens no menu") {
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi))
                    vm.onChromeToggled()

                    vm.tap(hitOn(cfi))

                    vm.uiState.value.highlightMenu.shouldBeNull()
                }
            }

            context("with a sheet open") {
                it("#299 TC-15: opens no menu") {
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi))
                    vm.onSettingsRequested()

                    vm.tap(hitOn(cfi))

                    vm.uiState.value.highlightMenu.shouldBeNull()
                }
            }
        }

        context("on a highlight made in this session") {
            it("#299 TC-12: takes a session highlight's text from its draft") {
                val cfi = randomCfi()
                val color = randomEnum<HighlightColor>()
                val word = randomString()
                val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(highlightsRepository = highlights)
                vm.selectText(cfi, text = "  $word  ")
                vm.onHighlightColorChosen(color)
                val hit = hitOn(cfi)

                vm.tap(hit)

                vm.uiState.value.highlightMenu shouldBe
                    HighlightMenu(listOf(HighlightMenuEntry(cfi, hit.rects, color, word, hasNote = false)))
            }
        }
    }

    describe("stepping the highlight menu") {
        context("with three highlights under the tap") {
            it("#299 TC-16: steps the active highlight") {
                val cfis = List(3) { randomCfi() }
                val vm = savedVm(*cfis.map { savedAt(cfi = it) }.toTypedArray())
                vm.tap(*cfis.map { hitOn(it) }.toTypedArray())
                val before = vm.uiState.value.highlightMenu.shouldNotBeNull()

                vm.onHighlightMenuStepped(1)

                vm.uiState.value.highlightMenu shouldBe before.copy(active = 1)
            }
        }
    }

    describe("closing the highlight menu") {
        context("on a relocate") {
            it("#299 TC-17: closes on any relocate, echo included") {
                checkAll(Exhaustive.boolean()) { echo ->
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi))
                    vm.tap(hitOn(cfi))

                    vm.onPageEvent(ReaderEvent.Relocate(randomRelocateData().copy(echo = echo)))

                    vm.uiState.value.highlightMenu.shouldBeNull()
                }
            }
        }

        context("on a load status change") {
            it("#299 TC-18: closes when the load status leaves Ready") {
                val statuses = Exhaustive.collection(
                    listOf<ReaderLoadStatus>(ReaderLoadStatus.Loading) +
                        ReaderLoadFailure.entries.map { ReaderLoadStatus.Failed(it) },
                )

                checkAll(statuses) { status ->
                    val cfi = randomCfi()
                    val vm = savedVm(savedAt(cfi))
                    vm.tap(hitOn(cfi))

                    vm.onPageEvent(ReaderEvent.LoadStatusChanged(status))

                    vm.uiState.value.highlightMenu.shouldBeNull()
                }
            }
        }

        context("on a repeated Ready status") {
            it("#299 TC-18: stays open on Ready") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi))
                vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))
                vm.tap(hitOn(cfi))
                val before = vm.uiState.value.highlightMenu.shouldNotBeNull()

                vm.onPageEvent(ReaderEvent.LoadStatusChanged(ReaderLoadStatus.Ready))

                vm.uiState.value.highlightMenu shouldBe before
            }
        }

        context("on a settings change") {
            it("#299 TC-19: closes on a settings change") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi))
                vm.tap(hitOn(cfi))
                val current = vm.uiState.value.settings

                vm.onSettingsChanged(current.copy(theme = ReaderTheme.entries.first { it != current.theme }))

                vm.uiState.value.highlightMenu.shouldBeNull()
            }
        }

        context("on unchanged settings") {
            it("#299 TC-19: stays open on unchanged settings") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi))
                vm.tap(hitOn(cfi))
                val before = vm.uiState.value.highlightMenu.shouldNotBeNull()

                vm.onSettingsChanged(vm.uiState.value.settings)

                vm.uiState.value.highlightMenu shouldBe before
            }
        }

        context("when the highlight is copied") {
            it("#299 TC-20: closes when the highlight is copied") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi))
                vm.tap(hitOn(cfi))

                vm.onHighlightCopied()

                vm.uiState.value.highlightMenu.shouldBeNull()
            }
        }
    }

    describe("the highlight menu when a save fails and its highlight is rolled back") {
        context("on a highlight made in this session") {
            it("#299 TC-22: closes when its highlight is rolled back") {
                // The save waits here, so the menu opens on the drawn highlight before the save fails.
                val gate = CompletableDeferred<Unit>()
                val highlights = TestHighlightsRepository().apply {
                    onAdd = {
                        gate.await()
                        HighlightWriteResult.Failed(HighlightError.Unreachable)
                    }
                }
                val vm = sut(highlightsRepository = highlights)
                val cfi = randomCfi()
                vm.selectText(cfi)
                vm.onHighlightColorChosen(randomEnum())
                vm.tap(hitOn(cfi))
                vm.uiState.value.highlightMenu.shouldNotBeNull()

                gate.complete(Unit)

                vm.uiState.value.highlightMenu.shouldBeNull()
            }
        }

        context("on a saved highlight") {
            it("#299 TC-22: stays open when another highlight is rolled back") {
                val highlights = TestHighlightsRepository().apply {
                    onAdd = { HighlightWriteResult.Failed(HighlightError.Unreachable) }
                }
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi), highlights = highlights)
                vm.tap(hitOn(cfi))
                val before = vm.uiState.value.highlightMenu.shouldNotBeNull()
                vm.selectText(randomCfi())

                vm.onHighlightColorChosen(randomEnum())

                vm.uiState.value.highlightMenu shouldBe before
            }
        }
    }
})

/** A saved highlight at [cfi], with a random colour and text. */
private fun savedAt(cfi: String): Highlight =
    randomHighlight().copy(location = cfi, color = randomEnum(), text = randomString())

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

/** A live selection over [cfi] with [text]. */
private fun ReaderViewModel.selectText(cfi: String, text: String = randomString()) =
    onPageEvent(ReaderEvent.Selection(randomSelectionData().copy(cfiRange = cfi, text = text)))
