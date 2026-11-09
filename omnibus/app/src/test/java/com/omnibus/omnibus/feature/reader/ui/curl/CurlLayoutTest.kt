package com.omnibus.omnibus.feature.reader.ui.curl

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.filter
import io.kotest.property.exhaustive.collection
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import kotlin.math.min

class CurlLayoutTest : DescribeSpec({

    /** A spine at [fraction] of the way across [bounds], which lies inside it for 0 < fraction < 1. */
    fun spineAt(bounds: CurlRect, fraction: Float): Float = bounds.left + fraction * bounds.width

    describe("the layout for a column count") {
        it("TC-313: is Single for one column") {
            CurlLayout.of(1) shouldBe CurlLayout.Single
        }

        it("TC-313: is Spread for two columns") {
            CurlLayout.of(2) shouldBe CurlLayout.Spread
        }

        it("TC-313: is null for any other column count") {
            checkAll(Exhaustive.collection<Int?>(listOf(null, -1, 0, 3, 4, 5, 6))) { columns ->
                CurlLayout.of(columns).shouldBeNull()
            }
        }
    }

    describe("the flap bounds") {
        context("for a single page") {
            it("TC-313: is the whole stage, whatever the spine") {
                checkAll(arbCurlRect(), Arb.numericFloat(-2f, 3f)) { bounds, fraction ->
                    CurlLayout.Single.flapBounds(bounds, spineAt(bounds, fraction)) shouldBe bounds
                }
            }
        }

        context("for a spread") {
            // Fractions 0 and 1 are excluded: the spine lands on an edge there and the band is the whole stage.
            it("TC-313: is centred on the spine, half as wide as the nearer edge") {
                checkAll(arbCurlRect(), Arb.numericFloat(0.01f, 0.99f)) { bounds, fraction ->
                    val spine = spineAt(bounds, fraction)
                    val band = CurlLayout.Spread.flapBounds(bounds, spine)
                    val tolerance = 1e-3f * bounds.width

                    ((band.left + band.right) / 2) shouldBe (spine plusOrMinus tolerance)
                    (band.width / 2) shouldBe (min(spine - bounds.left, bounds.right - spine) plusOrMinus tolerance)
                }
            }

            it("TC-313: spans the full height of the stage") {
                checkAll(arbCurlRect(), Arb.numericFloat(0.01f, 0.99f)) { bounds, fraction ->
                    val band = CurlLayout.Spread.flapBounds(bounds, spineAt(bounds, fraction))

                    band.top shouldBe bounds.top
                    band.bottom shouldBe bounds.bottom
                }
            }

            context("when the spine is not inside the stage") {
                it("TC-313: is the whole stage") {
                    checkAll(arbCurlRect(), Arb.numericFloat(-2f, 3f).filter { it <= 0f || it >= 1.01f }) { bounds, fraction ->
                        CurlLayout.Spread.flapBounds(bounds, spineAt(bounds, fraction)) shouldBe bounds
                    }
                }
            }

            context("when there is no spine") {
                it("TC-313: is the whole stage") {
                    checkAll(arbCurlRect()) { bounds ->
                        CurlLayout.Spread.flapBounds(bounds, null) shouldBe bounds
                    }
                }
            }
        }
    }

    describe("the turning page") {
        context("for a single page") {
            it("TC-313: is the whole stage") {
                checkAll(arbCurlRect()) { bounds ->
                    CurlLayout.Single.turning(bounds, spineAt(bounds, 0.5f)) shouldBe bounds
                }
            }
        }

        context("for a spread") {
            it("TC-313: is the right page, from the spine to the right edge") {
                checkAll(arbCurlRect(), Arb.numericFloat(0.01f, 0.99f)) { bounds, fraction ->
                    val spine = spineAt(bounds, fraction)
                    val page = CurlLayout.Spread.turning(bounds, spine)
                    val tolerance = 1e-3f * bounds.width

                    page.left shouldBe (spine plusOrMinus tolerance)
                    page.right shouldBe bounds.right
                    page.top shouldBe bounds.top
                    page.bottom shouldBe bounds.bottom
                }
            }
        }
    }
})
