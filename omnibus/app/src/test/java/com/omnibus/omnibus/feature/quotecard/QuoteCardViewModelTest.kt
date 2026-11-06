package com.omnibus.omnibus.feature.quotecard

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
private class Harness {
    val repository = FakeQuoteCardRepository()
    val vm = QuoteCardViewModel("book-1", PASSAGE, repository)
    val effects = mutableListOf<QuoteCardEffect>()
    private val scope = CoroutineScope(Job() + Dispatchers.Main).also { scope ->
        scope.launch { vm.effects.toCollection(effects) }
    }

    fun close() = scope.cancel()
}

private inline fun withHarness(block: Harness.() -> Unit) {
    val harness = Harness()
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
