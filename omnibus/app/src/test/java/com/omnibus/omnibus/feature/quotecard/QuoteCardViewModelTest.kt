package com.omnibus.omnibus.feature.quotecard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.cash.turbine.test
import com.omnibus.omnibus.data.quotecard.QuoteCardRepository
import com.omnibus.omnibus.data.quotecard.TestQuoteCardRepository
import com.omnibus.omnibus.data.quotecard.randomCoverPair
import com.omnibus.omnibus.data.quotecard.randomOpaqueArgb
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The two ways to ask for the card, which guard and fail alike. */
private enum class ExportRequest {
    Share {
        override fun QuoteCardViewModel.request(capture: suspend () -> ByteArray) = onShareRequested(capture)
    },
    Copy {
        override fun QuoteCardViewModel.request(capture: suspend () -> ByteArray) = onCopyRequested(capture)
    };

    abstract fun QuoteCardViewModel.request(capture: suspend () -> ByteArray)
}

private fun otherArgb(argb: Int): Int = generateSequence { randomOpaqueArgb() }.first { it != argb }

private fun randomPng(): ByteArray = randomString().toByteArray()

private fun randomFile(): File = File(randomString())

private fun randomSolid(): QuoteBackground = QuoteBackground.Wash(randomEnum<QuoteWash>())

private fun randomGradient(): QuoteBackground = QuoteBackground.Preset(randomEnum<QuoteGradientPreset>())

private fun randomFill(): QuoteFill = QuoteFill(
    from = Color(randomOpaqueArgb()),
    to = Color(randomOpaqueArgb()),
    angle = randomOf(listOf(0, 45, 90, 135, 180, 225, 270, 315)),
    ink = Color.White,
)

/** Starts a share of [png] that the repository holds in its save until the returned gate opens. */
private fun QuoteCardViewModel.shareHeldInSave(
    repository: TestQuoteCardRepository,
    png: ByteArray = randomPng(),
): CompletableDeferred<Unit> {
    val gate = CompletableDeferred<Unit>()
    repository.onSave = {
        gate.await()
        randomFile()
    }
    onShareRequested { png }
    return gate
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuoteCardViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        passage: String = randomString(),
        repository: QuoteCardRepository = TestQuoteCardRepository(),
    ) = QuoteCardViewModel(bookUuid, passage, repository)

    describe("opening the sheet") {
        it("shows the passage") {
            val passage = randomString()

            sut(passage = passage).uiState.value.passage shouldBe passage
        }

        it("starts on the book's own colour") {
            sut().uiState.value.background shouldBe QuoteBackground.Book
        }

        it("starts in Serif") {
            sut().uiState.value.typeface shouldBe QuoteTypeface.Serif
        }

        it("starts at size M") {
            sut().uiState.value.size shouldBe QuoteSize.M
        }

        it("starts upright") {
            sut().uiState.value.let { it.bold || it.italic } shouldBe false
        }

        it("starts on the Square shape") {
            sut().uiState.value.shape shouldBe QuoteShape.Square
        }

        it("starts not exporting") {
            sut().uiState.value.exporting shouldBe false
        }
    }

    describe("the cover's gradient pairs") {
        context("when the server offers pairs") {
            it("shows them") {
                val pairs = List(2) { randomCoverPair() }
                val repository = TestQuoteCardRepository().apply { coverPairs = pairs }

                sut(repository = repository).uiState.value.coverPairs shouldBe pairs
            }

            it("asks for the book's pairs once") {
                val uuid = randomString()
                val repository = TestQuoteCardRepository().apply { coverPairs = listOf(randomCoverPair()) }

                sut(bookUuid = uuid, repository = repository)

                repository.coverPairCalls shouldBe listOf(uuid)
            }
        }

        context("when the server offers none") {
            it("shows no pairs") {
                val repository = TestQuoteCardRepository().apply { coverPairs = emptyList() }

                sut(repository = repository).uiState.value.coverPairs.shouldBeEmpty()
            }
        }
    }

    describe("choosing a kind of background") {
        context("when the cover offers pairs") {
            context("when Gradient is chosen") {
                it("applies the cover's first pair") {
                    val pairs = List(2) { randomCoverPair() }
                    val vm = sut(repository = TestQuoteCardRepository().apply { coverPairs = pairs })

                    vm.onKindChosen(QuoteBackgroundKind.Gradient)

                    vm.uiState.value.background shouldBe QuoteBackground.Cover(pairs.first())
                }
            }
        }

        context("when the cover offers no pairs") {
            context("when Gradient is chosen") {
                it("applies the first preset") {
                    val vm = sut(repository = TestQuoteCardRepository().apply { coverPairs = emptyList() })

                    vm.onKindChosen(QuoteBackgroundKind.Gradient)

                    vm.uiState.value.background shouldBe QuoteBackground.Preset(QuoteGradientPreset.entries.first())
                }
            }
        }

        context("when the card is on a gradient") {
            context("when Solid is chosen") {
                it("applies the book's colour") {
                    val vm = sut()
                    vm.onBackgroundChosen(randomGradient())

                    vm.onKindChosen(QuoteBackgroundKind.Solid)

                    vm.uiState.value.background shouldBe QuoteBackground.Book
                }
            }
        }

        context("when the kind chosen is the one showing") {
            it("keeps the background") {
                val vm = sut()
                val wash = randomSolid()
                vm.onBackgroundChosen(wash)

                vm.onKindChosen(QuoteBackgroundKind.Solid)

                vm.uiState.value.background shouldBe wash
            }
        }
    }

    describe("choosing a background") {
        it("shows the one chosen") {
            val vm = sut()
            val background = randomSolid()

            vm.onBackgroundChosen(background)

            vm.uiState.value.background shouldBe background
        }

        it("leaves the other choices alone") {
            val vm = sut()
            val before = vm.uiState.value

            vm.onBackgroundChosen(randomSolid())

            vm.uiState.value.copy(background = before.background) shouldBe before
        }
    }

    describe("choosing a typeface") {
        it("changes only the typeface") {
            val vm = sut()
            val before = vm.uiState.value
            val other = randomOf(QuoteTypeface.entries - before.typeface)

            vm.onTypefaceChosen(other)

            vm.uiState.value shouldBe before.copy(typeface = other)
        }
    }

    describe("choosing a size") {
        it("changes only the size") {
            val vm = sut()
            val before = vm.uiState.value
            val other = randomOf(QuoteSize.entries - before.size)

            vm.onSizeChosen(other)

            vm.uiState.value shouldBe before.copy(size = other)
        }
    }

    describe("choosing a shape") {
        it("changes only the shape") {
            val vm = sut()
            val before = vm.uiState.value
            val other = randomOf(QuoteShape.entries - before.shape)

            vm.onShapeChosen(other)

            vm.uiState.value shouldBe before.copy(shape = other)
        }
    }

    describe("toggling bold") {
        it("turns bold on") {
            val vm = sut()
            val before = vm.uiState.value

            vm.onBoldToggled()

            vm.uiState.value shouldBe before.copy(bold = true)
        }

        context("when bold is on") {
            it("turns it off") {
                val vm = sut()
                val before = vm.uiState.value
                vm.onBoldToggled()

                vm.onBoldToggled()

                vm.uiState.value shouldBe before
            }
        }
    }

    describe("toggling italic") {
        it("turns italic on") {
            val vm = sut()
            val before = vm.uiState.value

            vm.onItalicToggled()

            vm.uiState.value shouldBe before.copy(italic = true)
        }

        context("when bold is on") {
            it("changes only italic") {
                val vm = sut()
                vm.onBoldToggled()
                val before = vm.uiState.value

                vm.onItalicToggled()

                vm.uiState.value shouldBe before.copy(italic = true)
            }
        }
    }

    describe("choosing a custom colour") {
        context("when the card is on a solid") {
            it("seeds a custom solid from the colour showing") {
                val vm = sut()
                vm.onBackgroundChosen(randomSolid())
                val fill = randomFill()

                vm.onCustomChosen(fill)

                vm.uiState.value.background shouldBe QuoteBackground.CustomSolid(fill.from.toArgb())
            }
        }

        context("when the card is on a gradient") {
            it("seeds a custom gradient from the stops and angle showing") {
                val vm = sut()
                vm.onBackgroundChosen(randomGradient())
                val fill = randomFill()

                vm.onCustomChosen(fill)

                vm.uiState.value.background shouldBe
                    QuoteBackground.CustomGradient(fill.from.toArgb(), fill.to.toArgb(), fill.angle)
            }
        }

        context("when a custom colour is already being edited") {
            it("keeps it") {
                val vm = sut()
                val editing = randomOf(
                    listOf(
                        QuoteBackground.CustomSolid(randomOpaqueArgb()),
                        QuoteBackground.CustomGradient(randomOpaqueArgb(), randomOpaqueArgb(), 90),
                    ),
                )
                vm.onBackgroundChosen(editing)

                vm.onCustomChosen(randomFill())

                vm.uiState.value.background shouldBe editing
            }
        }
    }

    describe("moving a colour slider") {
        context("when the background is a custom solid") {
            it("sets its colour") {
                val vm = sut()
                val old = randomOpaqueArgb()
                val new = otherArgb(old)
                vm.onBackgroundChosen(QuoteBackground.CustomSolid(old))

                vm.onCustomColorChanged(new)

                vm.uiState.value.background shouldBe QuoteBackground.CustomSolid(new)
            }
        }

        context("when the background is a custom gradient") {
            context("when its first stop is being edited") {
                it("sets the first stop") {
                    val vm = sut()
                    val gradient = QuoteBackground.CustomGradient(randomOpaqueArgb(), randomOpaqueArgb(), 90)
                    val new = otherArgb(gradient.fromArgb)
                    vm.onBackgroundChosen(gradient)

                    vm.onCustomColorChanged(new)

                    vm.uiState.value.background shouldBe gradient.copy(fromArgb = new)
                }
            }

            context("when its second stop is being edited") {
                it("sets the second stop") {
                    val vm = sut()
                    val gradient = QuoteBackground.CustomGradient(randomOpaqueArgb(), randomOpaqueArgb(), 90)
                    val new = otherArgb(gradient.toArgb)
                    vm.onBackgroundChosen(gradient)
                    vm.onStopFocused(GradientStop.To)

                    vm.onCustomColorChanged(new)

                    vm.uiState.value.background shouldBe gradient.copy(toArgb = new)
                }
            }
        }

        context("when the background isn't custom") {
            it("leaves it as it was") {
                val vm = sut()
                val background = randomOf(listOf(QuoteBackground.Book, randomSolid(), randomGradient()))
                vm.onBackgroundChosen(background)

                vm.onCustomColorChanged(randomOpaqueArgb())

                vm.uiState.value.background shouldBe background
            }
        }
    }

    describe("choosing the stop to edit") {
        it("makes it the stop the sliders edit") {
            val vm = sut()

            vm.onStopFocused(GradientStop.To)

            vm.uiState.value.activeStop shouldBe GradientStop.To
        }
    }

    describe("turning a gradient's direction") {
        context("when the background is a custom gradient") {
            it("turns it 45 degrees clockwise, wrapping at 360, from every multiple of 45") {
                checkAll(Exhaustive.collection((0 until 360 step 45).toList())) { angle ->
                    val vm = sut()
                    vm.onBackgroundChosen(QuoteBackground.CustomGradient(randomOpaqueArgb(), randomOpaqueArgb(), angle))

                    vm.onDirectionRotated()

                    (vm.uiState.value.background as QuoteBackground.CustomGradient).angle shouldBe (angle + 45) % 360
                }
            }
        }

        context("when the background has no direction of its own") {
            it("leaves it as it was") {
                val vm = sut()
                val background = randomOf(listOf(QuoteBackground.Book, randomSolid(), QuoteBackground.CustomSolid(randomOpaqueArgb())))
                vm.onBackgroundChosen(background)

                vm.onDirectionRotated()

                vm.uiState.value.background shouldBe background
            }
        }
    }

    describe("sharing the card") {
        context("when the card saves") {
            it("saves the bytes the capture produced") {
                val repository = TestQuoteCardRepository().apply { onSave = { randomFile() } }
                val png = randomPng()

                sut(repository = repository).onShareRequested { png }

                repository.saved.map { it.toList() } shouldBe listOf(png.toList())
            }

            it("emits Share with the saved file") {
                val file = randomFile()
                val vm = sut(repository = TestQuoteCardRepository().apply { onSave = { file } })

                vm.onShareRequested { randomPng() }

                vm.effects.test { awaitItem() shouldBe QuoteCardEffect.Share(file) }
            }

            it("stops exporting") {
                val vm = sut(repository = TestQuoteCardRepository().apply { onSave = { randomFile() } })

                vm.onShareRequested { randomPng() }

                vm.uiState.value.exporting shouldBe false
            }
        }

        context("when the card can't be saved") {
            it("emits nothing") {
                val vm = sut(repository = TestQuoteCardRepository().apply { onSave = { null } })

                vm.onShareRequested { randomPng() }

                vm.effects.test { expectNoEvents() }
            }

            it("stops exporting") {
                val vm = sut(repository = TestQuoteCardRepository().apply { onSave = { null } })

                vm.onShareRequested { randomPng() }

                vm.uiState.value.exporting shouldBe false
            }
        }

        context("when the capture fails") {
            it("saves nothing") {
                val repository = TestQuoteCardRepository()

                sut(repository = repository).onShareRequested { error(randomString()) }

                repository.saved.shouldBeEmpty()
            }

            it("emits nothing") {
                val vm = sut()

                vm.onShareRequested { error(randomString()) }

                vm.effects.test { expectNoEvents() }
            }

            it("stops exporting") {
                val vm = sut()

                vm.onShareRequested { error(randomString()) }

                vm.uiState.value.exporting shouldBe false
            }
        }

        context("when the card is still being captured") {
            it("is exporting") {
                val vm = sut()
                val gate = CompletableDeferred<Unit>()

                vm.onShareRequested {
                    gate.await()
                    randomPng()
                }

                vm.uiState.value.exporting shouldBe true
                gate.complete(Unit)
            }

            it("doesn't capture again for a second request, share or copy") {
                checkAll(Exhaustive.collection(ExportRequest.entries)) { second ->
                    val vm = sut()
                    val gate = CompletableDeferred<Unit>()
                    var captures = 0
                    vm.onShareRequested {
                        captures++
                        gate.await()
                        randomPng()
                    }

                    with(second) { vm.request { captures++; randomPng() } }
                    gate.complete(Unit)

                    captures shouldBe 1
                }
            }
        }

        context("when the card is still being saved") {
            it("saves only the first card for a second request, share or copy") {
                checkAll(Exhaustive.collection(ExportRequest.entries)) { second ->
                    val repository = TestQuoteCardRepository()
                    val vm = sut(repository = repository)
                    val first = randomPng()
                    val gate = vm.shareHeldInSave(repository, first)

                    with(second) { vm.request { randomPng() } }
                    gate.complete(Unit)

                    repository.saved.map { it.toList() } shouldBe listOf(first.toList())
                }
            }

            it("emits only the first card's effect for a second request") {
                val repository = TestQuoteCardRepository()
                val vm = sut(repository = repository)
                val gate = vm.shareHeldInSave(repository)

                vm.onCopyRequested { randomPng() }
                gate.complete(Unit)

                vm.effects.test {
                    awaitItem().shouldBeInstanceOf<QuoteCardEffect.Share>()
                    expectNoEvents()
                }
            }
        }
    }

    describe("copying the card") {
        context("when the card saves") {
            it("saves the bytes the capture produced") {
                val repository = TestQuoteCardRepository().apply { onSave = { randomFile() } }
                val png = randomPng()

                sut(repository = repository).onCopyRequested { png }

                repository.saved.map { it.toList() } shouldBe listOf(png.toList())
            }

            it("emits Copy with the saved file") {
                val file = randomFile()
                val vm = sut(repository = TestQuoteCardRepository().apply { onSave = { file } })

                vm.onCopyRequested { randomPng() }

                vm.effects.test { awaitItem() shouldBe QuoteCardEffect.Copy(file) }
            }
        }
    }
})
