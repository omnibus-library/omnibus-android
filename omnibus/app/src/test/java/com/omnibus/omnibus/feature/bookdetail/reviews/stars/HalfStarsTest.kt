package com.omnibus.omnibus.feature.bookdetail.reviews.stars

import com.omnibus.omnibus.data.reviews.randomRating
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class HalfStarsTest : DescribeSpec({

    describe("the half star under a position") {
        it("TC-1: is the step's own value at the middle of each of the ten steps") {
            val width = randomInt(1..1000).toFloat()

            (1..10).map { k -> halfStepAt((k - 0.5f) / 10f * width, width) } shouldBe
                listOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f)
        }

        it("TC-2: belongs to the lower step at each exact edge") {
            checkAll(Exhaustive.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)) { edge ->
                halfStepAt(edge.toFloat(), 10f) shouldBe edge / 2f
            }
        }

        it("TC-3: clamps for each input out of range") {
            checkAll(
                Exhaustive.of(
                    Triple(-1f, 10f, 0.5f),
                    Triple(Float.NEGATIVE_INFINITY, 10f, 0.5f),
                    Triple(11f, 10f, 5f),
                    Triple(Float.POSITIVE_INFINITY, 10f, 5f),
                    Triple(5f, 0f, 0.5f),
                    Triple(5f, -5f, 0.5f),
                    Triple(5f, Float.NaN, 0.5f),
                    Triple(Float.NaN, 10f, 0.5f),
                ),
            ) { (x, width, step) ->
                halfStepAt(x, width) shouldBe step
            }
        }
    }

    describe("tapping a star") {
        context("when it is the current rating") {
            it("TC-5: clears the rating") {
                val rating = randomRating()

                toggledRating(rating, rating) shouldBe null
            }
        }

        context("when it is not the current rating") {
            it("TC-5: sets the tapped value, for each of another rating, none") {
                checkAll(Exhaustive.of(4f, null)) { current ->
                    toggledRating(current, 3.5f) shouldBe 3.5f
                }
            }
        }
    }
})
