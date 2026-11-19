package com.omnibus.omnibus.feature.reader

import app.cash.turbine.test
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightDraft
import com.omnibus.omnibus.data.highlights.HighlightError
import com.omnibus.omnibus.data.highlights.HighlightWriteResult
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomSelectionData
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSelectionTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun ReaderViewModel.selectRange(cfi: String?, text: String = randomString()) =
        onPageEvent(ReaderEvent.Selection(randomSelectionData().copy(cfiRange = cfi, text = text)))

    describe("the page's selection") {
        it("#54 TC-22: holds the selection the page reports") {
            val data = randomSelectionData()
            val vm = sut()

            vm.onPageEvent(ReaderEvent.Selection(data))

            vm.uiState.value.selection shouldBe data
        }

        context("when a handle is grabbed over a selection") {
            it("#54 TC-22: clears the selection when the page reports it cleared") {
                val vm = sut()
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))
                vm.onSelectionHandleGrabbed()

                vm.onPageEvent(ReaderEvent.SelectionCleared)

                vm.uiState.value.selection shouldBe null
            }

            it("#54 TC-22: lets go of the handle when the page reports the selection cleared") {
                val vm = sut()
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))
                vm.onSelectionHandleGrabbed()

                vm.onPageEvent(ReaderEvent.SelectionCleared)

                vm.uiState.value.adjustingSelection shouldBe false
            }
        }
    }

    describe("choosing a highlight colour for the selection") {
        context("with a selection that has a range") {
            it("#54 TC-24: draws the range in the chosen colour at once") {
                val cfi = randomCfi()
                val color = randomEnum<HighlightColor>()
                val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(highlightsRepository = highlights)
                vm.selectRange(cfi)

                vm.onHighlightColorChosen(color)

                vm.uiState.value.pageHighlights shouldBe listOf(PageHighlight(cfi, color, hasNote = false))
            }

            it("#54 TC-24: clears the selection") {
                val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(highlightsRepository = highlights)
                vm.selectRange(randomCfi())

                vm.onHighlightColorChosen(randomEnum())

                vm.uiState.value.selection shouldBe null
            }

            it("#54 TC-24: asks the page to clear its selection") {
                val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(highlightsRepository = highlights)
                vm.selectRange(randomCfi())

                vm.onHighlightColorChosen(randomEnum())

                vm.commands.test {
                    awaitItem() shouldBe ReaderCommand.ClearSelection
                    expectNoEvents()
                }
            }

            it("#54 TC-25: saves one draft built from the range, the text and the colour for the book") {
                val uuid = randomString()
                val cfi = randomCfi()
                val text = randomString()
                val color = randomEnum<HighlightColor>()
                val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                val vm = sut(bookUuid = uuid, highlightsRepository = highlights)
                vm.selectRange(cfi, text = text)

                vm.onHighlightColorChosen(color)

                highlights.drafts shouldBe listOf(HighlightDraft.create(uuid, cfi, color, text))
            }
        }

        context("with a selection that has no range") {
            it("#54 TC-25: saves nothing for a null, empty or blank range") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { range ->
                    val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                    val vm = sut(highlightsRepository = highlights)
                    vm.selectRange(range)

                    vm.onHighlightColorChosen(randomEnum())

                    highlights.drafts.shouldBeEmpty()
                }
            }

            it("#54 TC-25: draws no highlight for a null, empty or blank range") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { range ->
                    val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                    val vm = sut(highlightsRepository = highlights)
                    vm.selectRange(range)

                    vm.onHighlightColorChosen(randomEnum())

                    vm.uiState.value.pageHighlights.shouldBeEmpty()
                }
            }

            it("#54 TC-25: clears the selection for a null, empty or blank range") {
                checkAll(Exhaustive.of<String?>(null, "", "   ")) { range ->
                    val highlights = TestHighlightsRepository().apply { onAdd = { awaitCancellation() } }
                    val vm = sut(highlightsRepository = highlights)
                    vm.selectRange(range)

                    vm.onHighlightColorChosen(randomEnum())

                    vm.uiState.value.selection shouldBe null
                }
            }
        }

        context("when the write fails") {
            val errors = Exhaustive.of(
                HighlightError.NoServer,
                HighlightError.Unreachable,
                HighlightError.SessionExpired,
                HighlightError.BookNotFound,
                HighlightError.Rejected,
                HighlightError.Http(500),
                HighlightError.UnexpectedResponse,
            )

            it("#54 TC-26: removes the drawn highlight, for each error") {
                checkAll(errors) { error ->
                    val highlights = TestHighlightsRepository().apply {
                        onAdd = { HighlightWriteResult.Failed(error) }
                    }
                    val vm = sut(highlightsRepository = highlights)
                    vm.selectRange(randomCfi())

                    vm.onHighlightColorChosen(randomEnum())

                    vm.uiState.value.pageHighlights.shouldBeEmpty()
                }
            }

            it("#54 TC-26: raises the save-failed notice, for each error") {
                checkAll(errors) { error ->
                    val highlights = TestHighlightsRepository().apply {
                        onAdd = { HighlightWriteResult.Failed(error) }
                    }
                    val vm = sut(highlightsRepository = highlights)
                    vm.selectRange(randomCfi())

                    vm.onHighlightColorChosen(randomEnum())

                    vm.uiState.value.notice shouldBe ReaderNotice.HighlightSaveFailed
                }
            }

            it("#54 TC-26: makes one write only, with no retry") {
                val highlights = TestHighlightsRepository().apply {
                    onAdd = { HighlightWriteResult.Failed(HighlightError.Unreachable) }
                }
                val vm = sut(highlightsRepository = highlights)
                vm.selectRange(randomCfi())

                vm.onHighlightColorChosen(randomEnum())

                highlights.drafts.size shouldBe 1
            }

            context("once the notice has been shown") {
                it("#54 TC-26: clears the notice") {
                    val highlights = TestHighlightsRepository().apply {
                        onAdd = { HighlightWriteResult.Failed(HighlightError.Unreachable) }
                    }
                    val vm = sut(highlightsRepository = highlights)
                    vm.selectRange(randomCfi())
                    vm.onHighlightColorChosen(randomEnum())

                    vm.onNoticeShown()

                    vm.uiState.value.notice shouldBe null
                }
            }
        }
    }

    describe("copying the selection") {
        context("with a selection up") {
            it("#54 TC-27: clears the selection") {
                val vm = sut()
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                vm.onSelectionCopied()

                vm.uiState.value.selection shouldBe null
            }

            it("#54 TC-27: asks the page to clear its selection") {
                val vm = sut()
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                vm.onSelectionCopied()

                vm.commands.test {
                    awaitItem() shouldBe ReaderCommand.ClearSelection
                    expectNoEvents()
                }
            }

            it("#54 TC-27: saves no highlight") {
                val highlights = TestHighlightsRepository()
                val vm = sut(highlightsRepository = highlights)
                vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                vm.onSelectionCopied()

                highlights.drafts.shouldBeEmpty()
            }
        }
    }

    describe("the page's selection under the reader's other intents") {
        context("with a selection up") {
            val intents = Exhaustive.of<(ReaderViewModel) -> Unit>(
                { it.onChromeToggled() },
                { it.onContentsRequested() },
                { it.onSettingsRequested() },
                { it.onMenuRowChosen(ReaderMenuRow.Contents) },
            )

            it("#54 TC-29: clears the selection, for each intent") {
                checkAll(intents) { intent ->
                    val vm = sut()
                    vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                    intent(vm)

                    vm.uiState.value.selection shouldBe null
                }
            }

            it("#54 TC-29: asks the page to clear its selection once, for each intent") {
                checkAll(intents) { intent ->
                    val vm = sut()
                    vm.onPageEvent(ReaderEvent.Selection(randomSelectionData()))

                    intent(vm)

                    vm.commands.test {
                        awaitItem() shouldBe ReaderCommand.ClearSelection
                        expectNoEvents()
                    }
                }
            }
        }
    }
})
