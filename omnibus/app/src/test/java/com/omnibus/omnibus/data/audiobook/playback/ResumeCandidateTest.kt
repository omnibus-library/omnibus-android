package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.randomResumePoint
import com.omnibus.omnibus.data.library.withRecord
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ResumeCandidateTest : DescribeSpec({

    describe("choosing the book to resume") {
        context("when a reading point is newer than a listening point") {
            // TC-47
            it("chooses the listening point (I-SYS-5)") {
                val reading = randomResumePoint().withRecord { copy(format = ProgressFormat.Reading) }
                val listening = randomResumePoint().withRecord { copy(format = ProgressFormat.Listening) }

                resumeCandidate(listOf(reading, listening)) { true } shouldBe listening
            }
        }

        context("when only an older listening point can open") {
            // TC-47
            it("chooses nothing (I-SYS-5)") {
                val newest = randomResumePoint().withRecord { copy(format = ProgressFormat.Listening) }
                val older = randomResumePoint().withRecord { copy(format = ProgressFormat.Listening) }

                resumeCandidate(listOf(newest, older)) { uuid -> uuid == older.record.bookUuid }.shouldBeNull()
            }
        }

        context("when there are no listening points") {
            // TC-47
            it("chooses nothing (I-SYS-5)") {
                val reading = randomResumePoint().withRecord { copy(format = ProgressFormat.Reading) }

                resumeCandidate(listOf(reading)) { true }.shouldBeNull()
            }
        }
    }
})
