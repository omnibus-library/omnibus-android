package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.library.testResumePoint
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ResumeCandidateTest : DescribeSpec({

    describe("resumeCandidate") {

        // TC-47
        it("picks the first listening point that can open (I-SYS-5)") {
            val listening = testResumePoint(book = testBook(1), format = ProgressFormat.Listening)
            val reading = testResumePoint(book = testBook(2), format = ProgressFormat.Reading)

            resumeCandidate(listOf(listening, reading)) { true } shouldBe listening
        }

        // TC-47
        it("never falls back to an older one (I-SYS-5)") {
            val newest = testResumePoint(book = testBook(1), format = ProgressFormat.Listening)
            val older = testResumePoint(book = testBook(2), format = ProgressFormat.Listening)

            resumeCandidate(listOf(newest, older)) { uuid -> uuid == "uuid-2" }.shouldBeNull()
        }

        // TC-47
        it("skips reading points (I-SYS-5)") {
            val reading = testResumePoint(book = testBook(1), format = ProgressFormat.Reading)
            val listening = testResumePoint(book = testBook(2), format = ProgressFormat.Listening)

            resumeCandidate(listOf(reading, listening)) { true } shouldBe listening
        }

        // TC-47
        it("is null when empty (I-SYS-5)") {
            resumeCandidate(emptyList()) { true }.shouldBeNull()

            val reading = testResumePoint(book = testBook(1), format = ProgressFormat.Reading)
            resumeCandidate(listOf(reading)) { true }.shouldBeNull()
        }
    }
})
