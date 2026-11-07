package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.library.testResumePoint
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ResumeCandidateTest : DescribeSpec({

    describe("choosing the book to resume") {
        context("when a reading point is newer than a listening point") {
            // TC-47
            it("chooses the listening point (I-SYS-5)") {
                val reading = testResumePoint(book = testBook(1), format = ProgressFormat.Reading)
                val listening = testResumePoint(book = testBook(2), format = ProgressFormat.Listening)

                resumeCandidate(listOf(reading, listening)) { true } shouldBe listening
            }
        }

        context("when only an older listening point can open") {
            // TC-47
            it("chooses nothing (I-SYS-5)") {
                val newest = testResumePoint(book = testBook(1), format = ProgressFormat.Listening)
                val older = testResumePoint(book = testBook(2), format = ProgressFormat.Listening)

                resumeCandidate(listOf(newest, older)) { uuid -> uuid == older.record.bookUuid }.shouldBeNull()
            }
        }

        context("when there are no listening points") {
            // TC-47
            it("chooses nothing (I-SYS-5)") {
                val reading = testResumePoint(book = testBook(1), format = ProgressFormat.Reading)

                resumeCandidate(listOf(reading)) { true }.shouldBeNull()
            }
        }
    }
})
