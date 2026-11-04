package com.omnibus.omnibus.data.audiobook.crossformat

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CrossFormatRulesTest : DescribeSpec({

    describe("shouldOffer") {

        // I-RES-7, P-SYNC-7: an ebook position with no dismissal on record is offered
        it("#166 TC-1: offers when no clock was stored (I-RES-7, P-SYNC-7)") {
            CrossFormatRules.shouldOffer(sourceClock = 100L, dismissed = null) shouldBe true
        }

        // P-SYNC-AC6: the dismissed clock is not offered again
        it("#166 TC-2: does not offer the clock that was dismissed (P-SYNC-AC6)") {
            CrossFormatRules.shouldOffer(sourceClock = 100L, dismissed = 100L) shouldBe false
        }

        // I-RES-7, A2: the offer returns when the clock changes, greater or smaller
        it("#166 TC-3: offers again when the clock differs, greater and smaller (I-RES-7, A2)") {
            CrossFormatRules.shouldOffer(sourceClock = 200L, dismissed = 100L) shouldBe true
            CrossFormatRules.shouldOffer(sourceClock = 50L, dismissed = 100L) shouldBe true
        }
    }

    describe("fileMatches") {

        // I-RES-7: a candidate applies to the loaded file only when its id is that file
        it("#166 TC-4: matches only the loaded file, never a null candidate file (I-RES-7)") {
            CrossFormatRules.fileMatches(loadedFileId = 11L, candidateFileId = 11L) shouldBe true
            CrossFormatRules.fileMatches(loadedFileId = 11L, candidateFileId = 12L) shouldBe false
            CrossFormatRules.fileMatches(loadedFileId = 11L, candidateFileId = null) shouldBe false
        }
    }
})
