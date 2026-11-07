package com.omnibus.omnibus.data.audiobook

import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ChooseAudioFileIdTest : DescribeSpec({

    describe("choosing the audio file to request") {

        context("when an explicit file is given") {
            // TC-1
            it("uses the explicit file over a saved one (I-LOAD-1)") {
                val explicit = randomLong()

                chooseAudioFileId(explicit = explicit, saved = randomLong()) shouldBe explicit
            }
        }

        context("when only a saved file exists") {
            // TC-1
            it("uses the saved file (I-LOAD-1)") {
                val saved = randomLong()

                chooseAudioFileId(explicit = null, saved = saved) shouldBe saved
            }
        }

        context("when no file is known") {
            // TC-1
            it("lets the server pick (I-LOAD-1)") {
                chooseAudioFileId(explicit = null, saved = null).shouldBeNull()
            }
        }
    }
})
