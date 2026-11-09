package com.omnibus.omnibus.ui.components

import com.omnibus.omnibus.testing.TEST_SEED
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class EdgeFadeTest : DescribeSpec({

    // The trailing false leaves out the NaN and infinite edge cases Arb.float adds, which are no fraction.

    describe("the stops of an edge fade") {

        it("TC-1: eases from about 96% alpha to exactly 0 in at least eight stops, for any solid fraction") {
            checkAll(PropTestConfig(seed = TEST_SEED), Arb.float(0f, 1f, false)) { fraction ->
                val stops = edgeFadeStops(fraction)
                val alphas = stops.map { it.second }

                stops.size shouldBeGreaterThanOrEqualTo 8
                stops.first().first shouldBe 0f
                stops.last().first shouldBe 1f
                stops.zipWithNext().all { (a, b) -> b.first >= a.first } shouldBe true
                alphas.first() shouldBe (0.96f plusOrMinus 1e-4f)
                alphas.last() shouldBe 0f
                alphas.zipWithNext().all { (a, b) -> b <= a } shouldBe true
                // No single step carries a third of the fade: the ease is spread over its stops, not a two-stop ramp.
                val range = alphas.first() - alphas.last()
                alphas.zipWithNext().forEach { (a, b) -> (a - b) shouldBeLessThanOrEqualTo range / 3f }
            }
        }
    }

    describe("the solid part of an edge fade") {

        it("TC-2: holds full solid alpha up to the solid fraction, then drops below it") {
            checkAll(PropTestConfig(seed = TEST_SEED), Arb.float(0f, 1f, false)) { fraction ->
                val stops = edgeFadeStops(fraction)
                val lastSolid = stops.indexOfLast { it.second == 0.96f }

                stops[lastSolid].first shouldBe (fraction plusOrMinus 1e-4f)
                stops[lastSolid + 1].second shouldBeLessThan 0.96f
            }
        }

        it("TC-2: is well formed when the solid part is empty or fills the whole fade") {
            checkAll(Exhaustive.of(0f, 1f)) { fraction ->
                val stops = edgeFadeStops(fraction)

                stops.all { (position, alpha) -> position.isFinite() && alpha.isFinite() } shouldBe true
                stops.zipWithNext().all { (a, b) -> b.first >= a.first } shouldBe true
                stops.last().second shouldBe 0f
            }
        }
    }
})
