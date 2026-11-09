package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlin.math.floor
import kotlin.math.sqrt

class CaptureScaleTest : DescribeSpec({

    describe("the scale of a capture") {
        context("when the capture is within the budget") {
            it("TC-318: is 1 when the capture exactly fills the budget") {
                checkAll(Arb.int(1..5_000), Arb.int(1..5_000)) { width, height ->
                    captureScale(width, height, width * height) shouldBe 1f
                }
            }

            it("TC-318: is 1 when the budget has room to spare") {
                checkAll(Arb.int(1..5_000), Arb.int(1..5_000)) { width, height ->
                    captureScale(width, height, width * height + randomInt(1..MAX_PIXELS)) shouldBe 1f
                }
            }
        }

        context("when the capture is over the budget") {
            it("TC-318: is the scale that brings its area to the budget, within 1%") {
                checkAll(Arb.int(2..20_000), Arb.int(2..20_000)) { width, height ->
                    val budget = randomInt(1 until width * height)
                    val exact = sqrt(budget.toDouble() / (width.toDouble() * height))

                    captureScale(width, height, budget).toDouble() shouldBe (exact plusOrMinus exact * 0.01)
                }
            }

            it("TC-318: keeps the floored capture within the budget") {
                checkAll(Arb.int(2..20_000), Arb.int(2..20_000)) { width, height ->
                    val budget = randomInt(1 until width * height)
                    val scale = captureScale(width, height, budget).toDouble()

                    (floor(width * scale) * floor(height * scale)) shouldBeLessThanOrEqualTo budget.toDouble()
                }
            }
        }

        context("when the capture has no area") {
            it("TC-318: is 1") {
                checkAll(Exhaustive.of(0 to 10, 10 to 0)) { (width, height) ->
                    captureScale(width, height, MAX_PIXELS) shouldBe 1f
                }
            }
        }

        it("TC-318: is never above 1") {
            checkAll(Arb.int(1..20_000), Arb.int(1..20_000), Arb.int(1..MAX_PIXELS)) { width, height, budget ->
                captureScale(width, height, budget).toDouble() shouldBeLessThanOrEqualTo 1.0
            }
        }
    }
})
