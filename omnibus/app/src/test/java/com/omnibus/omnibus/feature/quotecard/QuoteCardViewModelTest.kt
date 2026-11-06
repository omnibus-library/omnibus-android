package com.omnibus.omnibus.feature.quotecard

import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.data.quotecard.FakeQuoteCardRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val PASSAGE = "It was the best of times."

/** A ViewModel with its fake, and the effects it has emitted so far collected into [effects]. */
private class Harness(coverPairs: List<CoverPair>) {
    val repository = FakeQuoteCardRepository().also { it.coverPairs = coverPairs }
    val vm = QuoteCardViewModel("book-1", PASSAGE, repository)
    val effects = mutableListOf<QuoteCardEffect>()
    private val scope = CoroutineScope(Job() + Dispatchers.Main).also { scope ->
        scope.launch { vm.effects.toCollection(effects) }
    }

    fun close() = scope.cancel()
}

private inline fun withHarness(coverPairs: List<CoverPair> = emptyList(), block: Harness.() -> Unit) {
    val harness = Harness(coverPairs)
    try {
        harness.block()
    } finally {
        harness.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteCardViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("QuoteCardViewModel") {

        describe("initial state") {
            it("opens on the book's own colour, Serif, M, upright, Square, with the passage and not exporting") {
                withHarness {
                    vm.uiState.value shouldBe QuoteCardUIState(
                        passage = PASSAGE,
                        background = QuoteBackground.Book,
                        typeface = QuoteTypeface.Serif,
                        size = QuoteSize.M,
                        bold = false,
                        italic = false,
                        shape = QuoteShape.Square,
                        exporting = false,
                    )
                }
            }
        }

        describe("cover pairs") {
            it("fetches the book's pairs once on creation and shows them") {
                val pairs = listOf(CoverPair(0xFF112233.toInt(), 0xFF445566.toInt(), lightInk = true))

                withHarness(coverPairs = pairs) {
                    vm.uiState.value.coverPairs shouldBe pairs
                    repository.coverPairCalls shouldBe listOf("book-1")
                }
            }

            it("leaves the state at its default when the server offers none") {
                withHarness {
                    vm.uiState.value.coverPairs shouldBe emptyList()
                    vm.uiState.value shouldBe QuoteCardUIState(passage = PASSAGE)
                }
            }
        }

        describe("onKindChosen") {
            val pair = CoverPair(0xFF112233.toInt(), 0xFF445566.toInt(), lightInk = true)

            it("applies the first cover pair when Gradient is chosen and the cover offers pairs") {
                withHarness(coverPairs = listOf(pair, pair.copy(lightInk = false))) {
                    vm.onKindChosen(QuoteBackgroundKind.Gradient)

                    vm.uiState.value.background shouldBe QuoteBackground.Cover(pair)
                }
            }

            it("applies Ember when Gradient is chosen and the cover offers no pairs") {
                withHarness {
                    vm.onKindChosen(QuoteBackgroundKind.Gradient)

                    vm.uiState.value.background shouldBe QuoteBackground.Preset(QuoteGradientPreset.Ember)
                }
            }

            it("applies the book's colour when Solid is chosen from a gradient") {
                withHarness {
                    vm.onBackgroundChosen(QuoteBackground.Preset(QuoteGradientPreset.Dawn))

                    vm.onKindChosen(QuoteBackgroundKind.Solid)

                    vm.uiState.value.background shouldBe QuoteBackground.Book
                }
            }

            it("keeps the background when the current kind is chosen again") {
                withHarness {
                    vm.onBackgroundChosen(QuoteBackground.Wash(QuoteWash.Moss))

                    vm.onKindChosen(QuoteBackgroundKind.Solid)

                    vm.uiState.value.background shouldBe QuoteBackground.Wash(QuoteWash.Moss)
                }
            }
        }

        describe("onBackgroundChosen") {
            it("sets the background and leaves the other choices alone") {
                withHarness {
                    vm.onBackgroundChosen(QuoteBackground.Wash(QuoteWash.Rose))

                    vm.uiState.value shouldBe QuoteCardUIState(
                        passage = PASSAGE,
                        background = QuoteBackground.Wash(QuoteWash.Rose),
                    )
                }
            }
        }

        describe("onShareRequested") {
            it("saves the bytes, emits Share with the file, and is not exporting afterwards") {
                withHarness {
                    val file = File("saved/omnibus-quote.png")
                    repository.saveResult = file

                    vm.onShareRequested(byteArrayOf(1, 2, 3))

                    repository.saved.map { it.toList() } shouldBe listOf(listOf<Byte>(1, 2, 3))
                    effects shouldBe listOf(QuoteCardEffect.Share(file))
                    vm.uiState.value.exporting shouldBe false
                }
            }

            it("is exporting while the save is in flight, and ignores a second request") {
                withHarness {
                    val gate = CompletableDeferred<Unit>().also { repository.saveGate = it }

                    vm.onShareRequested(byteArrayOf(1))
                    vm.uiState.value.exporting shouldBe true
                    vm.onShareRequested(byteArrayOf(2))
                    vm.onCopyRequested(byteArrayOf(3))
                    gate.complete(Unit)

                    repository.saved.map { it.toList() } shouldBe listOf(listOf<Byte>(1))
                    effects.size shouldBe 1
                    vm.uiState.value.exporting shouldBe false
                }
            }

            it("emits nothing and stops exporting when the card can't be saved") {
                withHarness {
                    repository.saveResult = null

                    vm.onShareRequested(byteArrayOf(1))

                    effects shouldBe emptyList()
                    vm.uiState.value.exporting shouldBe false
                }
            }
        }

        describe("onCopyRequested") {
            it("saves the bytes and emits Copy with the file") {
                withHarness {
                    val file = File("saved/omnibus-quote.png")
                    repository.saveResult = file

                    vm.onCopyRequested(byteArrayOf(9))

                    effects shouldBe listOf(QuoteCardEffect.Copy(file))
                    vm.uiState.value.exporting shouldBe false
                }
            }
        }
    }
})
