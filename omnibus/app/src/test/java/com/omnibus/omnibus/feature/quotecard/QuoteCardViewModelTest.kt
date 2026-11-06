package com.omnibus.omnibus.feature.quotecard

import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.data.quotecard.FakeQuoteCardRepository
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
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

        describe("type and shape intents") {
            val defaults = QuoteCardUIState(passage = PASSAGE)

            it("onTypefaceChosen sets only the typeface") {
                withHarness {
                    vm.onTypefaceChosen(QuoteTypeface.Mono)

                    vm.uiState.value shouldBe defaults.copy(typeface = QuoteTypeface.Mono)
                }
            }

            it("onSizeChosen sets only the size") {
                withHarness {
                    vm.onSizeChosen(QuoteSize.L)

                    vm.uiState.value shouldBe defaults.copy(size = QuoteSize.L)
                }
            }

            it("onBoldToggled flips only bold, and back") {
                withHarness {
                    vm.onBoldToggled()
                    vm.uiState.value shouldBe defaults.copy(bold = true)

                    vm.onBoldToggled()
                    vm.uiState.value shouldBe defaults
                }
            }

            it("onItalicToggled flips only italic, independent of bold") {
                withHarness {
                    vm.onBoldToggled()
                    vm.onItalicToggled()

                    vm.uiState.value shouldBe defaults.copy(bold = true, italic = true)
                }
            }

            it("onShapeChosen sets only the shape") {
                withHarness {
                    vm.onShapeChosen(QuoteShape.Story)

                    vm.uiState.value shouldBe defaults.copy(shape = QuoteShape.Story)
                }
            }
        }

        describe("custom colours") {
            val book = Oklch(0.5, 0.1, 30.0)
            val ember = QuoteBackground.Preset(QuoteGradientPreset.Ember)

            it("onCustomChosen seeds a custom solid from the current solid's base colour") {
                withHarness {
                    vm.onBackgroundChosen(QuoteBackground.Wash(QuoteWash.Moss))
                    val fill = vm.uiState.value.background.fill(book)

                    vm.onCustomChosen(fill)

                    vm.uiState.value.background shouldBe QuoteBackground.CustomSolid(fill.from.toArgb())
                }
            }

            it("onCustomChosen seeds a custom gradient from the current gradient's stops and angle") {
                withHarness {
                    vm.onBackgroundChosen(ember)

                    vm.onCustomChosen(vm.uiState.value.background.fill(book))

                    vm.uiState.value.background shouldBe
                        QuoteBackground.CustomGradient(0xFF7A1F16.toInt(), 0xFFA24C16.toInt(), 135)
                }
            }

            it("onCustomChosen keeps a custom colour already being edited") {
                withHarness {
                    val custom = QuoteBackground.CustomGradient(0xFF102030.toInt(), 0xFF304050.toInt(), 270)
                    vm.onBackgroundChosen(custom)

                    vm.onCustomChosen(custom.fill(book))

                    vm.uiState.value.background shouldBe custom
                }
            }

            it("onCustomColorChanged sets a custom solid's colour") {
                withHarness {
                    vm.onBackgroundChosen(QuoteBackground.CustomSolid(0xFF112233.toInt()))

                    vm.onCustomColorChanged(0xFF445566.toInt())

                    vm.uiState.value.background shouldBe QuoteBackground.CustomSolid(0xFF445566.toInt())
                }
            }

            it("onCustomColorChanged sets only the active stop of a custom gradient") {
                withHarness {
                    vm.onBackgroundChosen(QuoteBackground.CustomGradient(0xFF112233.toInt(), 0xFF445566.toInt(), 90))

                    vm.onCustomColorChanged(0xFFAAAAAA.toInt())
                    vm.onStopFocused(GradientStop.To)
                    vm.onCustomColorChanged(0xFFBBBBBB.toInt())

                    vm.uiState.value.activeStop shouldBe GradientStop.To
                    vm.uiState.value.background shouldBe
                        QuoteBackground.CustomGradient(0xFFAAAAAA.toInt(), 0xFFBBBBBB.toInt(), 90)
                }
            }

            it("onCustomColorChanged does nothing when the background isn't custom") {
                withHarness {
                    vm.onBackgroundChosen(ember)

                    vm.onCustomColorChanged(0xFFFFFFFF.toInt())

                    vm.uiState.value.background shouldBe ember
                }
            }

            it("onDirectionRotated turns a custom gradient 45 degrees at a time and wraps at 360") {
                withHarness {
                    vm.onBackgroundChosen(QuoteBackground.CustomGradient(0xFF112233.toInt(), 0xFF445566.toInt(), 315))

                    vm.onDirectionRotated()
                    (vm.uiState.value.background as QuoteBackground.CustomGradient).angle shouldBe 0

                    vm.onDirectionRotated()
                    (vm.uiState.value.background as QuoteBackground.CustomGradient).angle shouldBe 45
                }
            }

            it("onDirectionRotated does nothing for a background without a direction of its own") {
                withHarness {
                    vm.onDirectionRotated()

                    vm.uiState.value.background shouldBe QuoteBackground.Book
                }
            }

            it("flips a custom gradient's ink to dark when a stop is dragged to near-white") {
                withHarness {
                    vm.onBackgroundChosen(ember)
                    vm.onCustomChosen(vm.uiState.value.background.fill(book))
                    vm.uiState.value.background.fill(book).ink shouldBe LightInk

                    vm.onCustomColorChanged(0xFFFAF8F4.toInt())

                    vm.uiState.value.background.fill(book).ink shouldBe DarkInk
                }
            }

            it("flips a custom solid's ink to dark when it is dragged to near-white") {
                withHarness {
                    vm.onCustomChosen(vm.uiState.value.background.fill(book))
                    vm.uiState.value.background.fill(book).ink.luminance() shouldBeGreaterThan 0.5f

                    vm.onCustomColorChanged(0xFFFAF8F4.toInt())

                    vm.uiState.value.background.fill(book).ink.luminance() shouldBeLessThan 0.1f
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
