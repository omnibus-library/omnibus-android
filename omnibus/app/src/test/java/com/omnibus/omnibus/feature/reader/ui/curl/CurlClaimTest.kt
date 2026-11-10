package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** The slop the tests that don't vary it use. */
private const val SLOP = 8f

private fun randomSign(): Float = if (randomBoolean()) 1f else -1f

class CurlClaimTest : DescribeSpec({

    /**
     * A one-pointer move of [dx] and [dy] after [elapsedMs] that is draggable and announced by default. Each test
     * overrides only the input its context claims. The defaults are random, and draggable for any slop they draw.
     */
    fun decide(
        slop: Float = randomDouble(1.0, 40.0).toFloat(),
        dx: Float = randomSign() * (slop + randomDouble(0.0, 200.0).toFloat()),
        dy: Float = dx * randomDouble(-0.9, 0.9).toFloat(),
        elapsedMs: Long = randomLong(0L..(CurlClaim.LONG_PRESS_MS - 1)),
        pointers: Int = 1,
        selectionLive: Boolean = false,
        announced: (Int) -> Boolean = { true },
        canDrag: (Int) -> Boolean = { true },
    ): CurlClaim = CurlClaim.decide(dx, dy, slop, elapsedMs, pointers, selectionLive, announced, canDrag)

    describe("claiming a touch") {
        it("TC-315: drags a leftward swipe in the forward direction") {
            checkAll(Arb.numericFloat(SLOP, 300f)) { distance ->
                decide(slop = SLOP, dx = -distance, dy = 0f) shouldBe CurlClaim.Drag(1)
            }
        }

        it("TC-315: drags a rightward swipe in the back direction") {
            checkAll(Arb.numericFloat(SLOP, 300f)) { distance ->
                decide(slop = SLOP, dx = distance, dy = 0f) shouldBe CurlClaim.Drag(-1)
            }
        }

        context("when the move is under the slop") {
            it("TC-315: passes") {
                checkAll(Arb.numericFloat(1f, 40f)) { slop ->
                    decide(slop = slop, dx = -(slop - 0.01f), dy = 0f) shouldBe CurlClaim.Pass
                }
            }
        }

        context("when there is more than one pointer") {
            it("TC-315: passes") {
                checkAll(Exhaustive.of(2, 3, 4, 5)) { pointers ->
                    decide(slop = SLOP, dx = -50f, dy = 0f, pointers = pointers) shouldBe CurlClaim.Pass
                }
            }
        }

        context("when the touch has lasted the long-press time") {
            it("TC-315: passes") {
                checkAll(Exhaustive.of(CurlClaim.LONG_PRESS_MS, CurlClaim.LONG_PRESS_MS + 1, 5_000L)) { elapsed ->
                    decide(slop = SLOP, dx = -50f, dy = 0f, elapsedMs = elapsed) shouldBe CurlClaim.Pass
                }
            }
        }

        context("when a selection is live") {
            it("TC-315: passes") {
                decide(slop = SLOP, dx = -50f, dy = 0f, selectionLive = true) shouldBe CurlClaim.Pass
            }
        }

        context("when the direction is not announced") {
            it("TC-315: passes") {
                decide(slop = SLOP, dx = -50f, dy = 0f, announced = { false }) shouldBe CurlClaim.Pass
            }
        }

        context("when the direction is announced but cannot be dragged") {
            it("TC-315: turns a move that is more horizontal than vertical") {
                checkAll(Arb.numericFloat(SLOP, 300f), Arb.numericFloat(-0.9f, 0.9f)) { distance, ratio ->
                    decide(slop = SLOP, dx = -distance, dy = ratio * distance, canDrag = { false }) shouldBe CurlClaim.Turn(1)
                }
            }

            it("TC-315: passes a move that is more vertical than horizontal") {
                checkAll(Arb.numericFloat(SLOP, 300f), Arb.numericFloat(1.1f, 5f)) { distance, ratio ->
                    decide(slop = SLOP, dx = -distance, dy = ratio * distance, canDrag = { false }) shouldBe CurlClaim.Pass
                }
            }
        }
    }
})
