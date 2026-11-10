package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.data.reader.ReaderPageTurn
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of

/** One combination of the inputs [effectiveTurn] takes. */
private data class TurnInput(
    val mode: ReaderPageTurn,
    val reducedMotion: Boolean,
    val lowRam: Boolean,
    val layout: CurlLayout?,
)

/** Every combination of the four inputs, with the layout null, single or spread. */
private fun allTurnInputs(): List<TurnInput> = ReaderPageTurn.entries.flatMap { mode ->
    listOf(false, true).flatMap { reducedMotion ->
        listOf(false, true).flatMap { lowRam ->
            listOf<CurlLayout?>(null, CurlLayout.Single, CurlLayout.Spread).map { layout ->
                TurnInput(mode, reducedMotion, lowRam, layout)
            }
        }
    }
}

class EffectiveTurnTest : DescribeSpec({

    /** Checks that every combination matching [matching] plays [expected]. */
    suspend fun checkTurns(expected: EffectiveTurn, matching: (TurnInput) -> Boolean) =
        checkAll(Exhaustive.collection(allTurnInputs().filter(matching))) { input ->
            effectiveTurn(input.mode, input.reducedMotion, input.lowRam, input.layout) shouldBe expected
        }

    describe("the turn that plays") {
        context("in Slide mode") {
            it("TC-317: slides, whatever the other inputs") {
                checkTurns(EffectiveTurn.Slide) { it.mode == ReaderPageTurn.Slide }
            }
        }

        context("in None mode") {
            it("TC-317: lands at once, whatever the other inputs") {
                checkTurns(EffectiveTurn.Instant) { it.mode == ReaderPageTurn.None }
            }
        }

        context("in Curl mode") {
            context("on a device that is low on RAM") {
                it("TC-317: slides, whatever the layout and motion setting") {
                    checkTurns(EffectiveTurn.Slide) { it.mode == ReaderPageTurn.Curl && it.lowRam }
                }
            }

            context("on a device that is not low on RAM") {
                context("with no layout") {
                    it("TC-317: slides, whatever the motion setting") {
                        checkTurns(EffectiveTurn.Slide) { it.mode == ReaderPageTurn.Curl && !it.lowRam && it.layout == null }
                    }
                }

                context("with a layout") {
                    context("when reduced motion is on") {
                        it("TC-317: lands at once") {
                            checkTurns(EffectiveTurn.Instant) {
                                it.mode == ReaderPageTurn.Curl && !it.lowRam && it.layout != null && it.reducedMotion
                            }
                        }
                    }

                    context("when reduced motion is off") {
                        it("TC-317: curls") {
                            checkTurns(EffectiveTurn.Curl) {
                                it.mode == ReaderPageTurn.Curl && !it.lowRam && it.layout != null && !it.reducedMotion
                            }
                        }
                    }
                }
            }
        }
    }
})
