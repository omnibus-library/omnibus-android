package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.RelocateData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class PersistGateTest : DescribeSpec({

    fun relocate(cfi: String?, settled: Int? = null, echo: Boolean = false) =
        RelocateData(cfi = cfi, pct = 10, totalPages = 100, echo = echo, settledSeek = settled)

    describe("PersistGate") {

        // TC-93: the hold releases only on the reader's own move, not on the seek's landing or a repeat.
        it("TC-93: the hold releases only on the reader's own move") {
            val gate = PersistGate(held = true)
            gate.onSeekSent()

            val admitted = listOf(
                relocate("a"),
                relocate("b", settled = 1),
                relocate("b"),
                relocate("c"),
            ).map(gate::admit)

            admitted shouldContainExactly listOf(false, false, false, true)
            gate.held shouldBe false
        }

        // TC-94: a relocate that settles the seek, or an echo, never releases; an unheld gate admits everything.
        it("TC-94: a relocate that settles the seek or is an echo never releases") {
            val held = PersistGate(held = true)
            held.onSeekSent()

            val admitted = listOf(
                relocate("x", settled = 1),
                relocate("y", echo = true),
                relocate("z"),
            ).map(held::admit)

            admitted shouldContainExactly listOf(false, false, true)

            PersistGate(held = false).admit(relocate("a")) shouldBe true
        }
    }
})
