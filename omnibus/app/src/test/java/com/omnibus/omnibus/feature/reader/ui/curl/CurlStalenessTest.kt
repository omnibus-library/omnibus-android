package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** A staleness that has just finished a capture at [cfi], so the snapshots show that position and are fresh. */
private fun capturedAt(cfi: String): CurlStaleness = CurlStaleness().apply { captured(cfi, cfi) }

class CurlStalenessTest : DescribeSpec({

    describe("a page change under the snapshots") {
        context("when a capture finished at a position") {
            context("when the host is busy") {
                context("when the page relocates to a new position") {
                    it("TC-322: ignores the relocate") {
                        val at = randomString()

                        capturedAt(at).pageChanged(byRelocate = true, cfi = "$at-moved", busy = true) shouldBe false
                    }
                }
            }

            context("when the host is idle") {
                context("when the page relocates to the captured position") {
                    it("TC-322: ignores the relocate") {
                        val at = randomString()

                        capturedAt(at).pageChanged(byRelocate = true, cfi = at, busy = false) shouldBe false
                    }
                }

                context("when the page relocates to a new position") {
                    it("TC-322: marks the snapshots stale") {
                        val at = randomString()

                        capturedAt(at).pageChanged(byRelocate = true, cfi = "$at-moved", busy = false) shouldBe true
                    }
                }

                context("when the page repainted since the capture") {
                    context("when the page relocates to the captured position") {
                        it("TC-322: marks the snapshots stale") {
                            val at = randomString()
                            val staleness = capturedAt(at)
                            staleness.pageChanged(byRelocate = false, cfi = at, busy = false)

                            staleness.pageChanged(byRelocate = true, cfi = at, busy = false) shouldBe true
                        }
                    }
                }
            }
        }
    }
})
