package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class PersistGateTest : DescribeSpec({

    fun relocate(cfi: String? = randomCfi(), settled: Int? = null, echo: Boolean = false) =
        randomRelocateData().copy(cfi = cfi, echo = echo, settledSeek = settled)

    describe("admitting a relocate for saving") {
        context("when held after a seek was sent") {
            it("TC-93: admits only the reader's own move off the seek's landing") {
                val gate = PersistGate(held = true)
                gate.onSeekSent()
                val landing = randomCfi()

                listOf(relocate(), relocate(landing, settled = 1), relocate(landing), relocate())
                    .map(gate::admit) shouldContainExactly listOf(false, false, false, true)
            }

            it("TC-94: is not released by an echo") {
                val gate = PersistGate(held = true)
                gate.onSeekSent()

                listOf(relocate(settled = 1), relocate(echo = true), relocate())
                    .map(gate::admit) shouldContainExactly listOf(false, false, true)
            }
        }

        context("when not held") {
            it("TC-94: admits the relocate") {
                PersistGate(held = false).admit(relocate()) shouldBe true
            }
        }
    }
})
