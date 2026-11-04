package com.omnibus.omnibus.data.audiobook

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ChooseAudioFileIdTest : DescribeSpec({

    describe("chooseAudioFileId") {

        // TC-1
        it("explicit wins over saved (I-LOAD-1)") {
            chooseAudioFileId(explicit = 33L, saved = 22L) shouldBe 33L
        }

        // TC-1
        it("falls back to saved (I-LOAD-1)") {
            chooseAudioFileId(explicit = null, saved = 22L) shouldBe 22L
        }

        // TC-1
        it("null lets the server pick (I-LOAD-1)") {
            chooseAudioFileId(explicit = null, saved = null).shouldBeNull()
        }
    }
})
