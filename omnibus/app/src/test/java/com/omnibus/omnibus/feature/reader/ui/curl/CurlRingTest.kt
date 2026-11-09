package com.omnibus.omnibus.feature.reader.ui.curl

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.exhaustive.of
import io.kotest.property.checkAll

class CurlRingTest : DescribeSpec({

    describe("landing a turn on the ring") {
        context("with a destination") {
            it("TC-314: puts the destination in front") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val ring = CurlRing(current = Any(), next = Any(), previous = Any())
                    val destination = Any()

                    ring.landed(dir, destination)[0] shouldBe destination
                }
            }

            it("TC-314: moves the old front behind it") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val front = Any()
                    val ring = CurlRing(current = front, next = Any(), previous = Any())

                    ring.landed(dir, Any())[-dir] shouldBe front
                }
            }

            it("TC-314: leaves the slot ahead empty") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val ring = CurlRing(current = Any(), next = Any(), previous = Any())

                    ring.landed(dir, Any())[dir].shouldBeNull()
                }
            }

            it("TC-314: drops both old neighbours") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val next = Any()
                    val previous = Any()
                    val ring = CurlRing(current = Any(), next = next, previous = previous)

                    ring.dropped(ring.landed(dir, Any())) shouldContainExactlyInAnyOrder listOf(next, previous)
                }
            }
        }

        context("without a destination") {
            it("TC-314: moves the neighbour in that direction to the front") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val ring = CurlRing(current = Any(), next = Any(), previous = Any())

                    ring.landed(dir)[0] shouldBe ring[dir]
                }
            }

            it("TC-314: moves the old front behind it") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val front = Any()
                    val ring = CurlRing(current = front, next = Any(), previous = Any())

                    ring.landed(dir)[-dir] shouldBe front
                }
            }

            it("TC-314: leaves the slot ahead empty") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val ring = CurlRing(current = Any(), next = Any(), previous = Any())

                    ring.landed(dir)[dir].shouldBeNull()
                }
            }

            it("TC-314: drops only the neighbour on the far side") {
                checkAll(Exhaustive.of(1, -1)) { dir ->
                    val next = Any()
                    val previous = Any()
                    val ring = CurlRing(current = Any(), next = next, previous = previous)

                    ring.dropped(ring.landed(dir)) shouldContainExactlyInAnyOrder listOf(ring[-dir])
                }
            }
        }
    }
})
