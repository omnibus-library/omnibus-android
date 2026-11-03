package com.omnibus.omnibus.feature.bookdetail.more.merge

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.merge.MergeError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MergeRulesTest : DescribeSpec({

    describe("mergeRow") {

        it("#120 TC-1: only an admin with files sees the row, and it is disabled offline") {
            val combinations = listOf(null, false, true).flatMap { admin ->
                listOf(false, true).flatMap { files ->
                    listOf(false, true).map { online -> Triple(admin, files, online) }
                }
            }
            val expected = listOf(
                MergeRow.Hidden, MergeRow.Hidden, MergeRow.Hidden, MergeRow.Hidden, // admin null
                MergeRow.Hidden, MergeRow.Hidden, MergeRow.Hidden, MergeRow.Hidden, // admin false
                MergeRow.Hidden, MergeRow.Hidden, MergeRow.DisabledOffline, MergeRow.Enabled, // admin true, files
            )

            combinations.map { (admin, files, online) -> mergeRow(admin, files, online) } shouldBe expected
        }
    }

    describe("mergeCandidates") {

        it("#120 TC-2: the target is removed and the order is kept") {
            val a = mergeTestBook(1)
            val target = mergeTestBook(2, uuid = "target")
            val b = mergeTestBook(3)

            mergeCandidates(listOf(a, target, b), "target") shouldBe listOf(a, b)
        }

        it("#120 TC-2: an empty list and a list of only the target give no candidates") {
            mergeCandidates(emptyList(), "target") shouldBe emptyList()
            mergeCandidates(listOf(mergeTestBook(2, uuid = "target")), "target") shouldBe emptyList()
        }
    }

    describe("candidateTags") {

        val target = mergeTestBook(1, uuid = "target", seriesId = 5L)

        it("#120 TC-3: same series and formats join with the separator, with formats upper-cased") {
            val candidate = mergeTestBook(2, seriesId = 5L, formats = listOf("epub", "m4b"))

            candidateTags(candidate, target, "Same series") shouldBe "Same series · EPUB, M4B"
        }

        it("#120 TC-3: same series with no formats is the series label alone") {
            val candidate = mergeTestBook(2, seriesId = 5L, formats = emptyList())

            candidateTags(candidate, target, "Same series") shouldBe "Same series"
        }

        it("#120 TC-3: no series with mixed-case formats gives upper-cased formats") {
            val candidate = mergeTestBook(2, formats = listOf("Epub", "m4B"))

            candidateTags(candidate, mergeTestBook(1), "Same series") shouldBe "EPUB, M4B"
        }

        it("#120 TC-3: no series with one format gives that format") {
            candidateTags(mergeTestBook(2, formats = listOf("epub")), mergeTestBook(1), "Same series") shouldBe "EPUB"
        }

        it("#120 TC-3: no series and no formats gives an empty line") {
            candidateTags(mergeTestBook(2, formats = emptyList()), mergeTestBook(1), "Same series") shouldBe ""
        }

        it("#120 TC-4: different series ids are not the same series") {
            val candidate = mergeTestBook(2, seriesId = 6L, formats = listOf("epub"))

            candidateTags(candidate, target, "Same series") shouldBe "EPUB"
        }

        it("#120 TC-4: a missing series id on either side is not the same series, even when both are missing") {
            val candidateNull = mergeTestBook(2, seriesId = null, formats = listOf("epub"))
            val targetNull = mergeTestBook(1, uuid = "target", seriesId = null)

            candidateTags(candidateNull, target, "Same series") shouldBe "EPUB"
            candidateTags(mergeTestBook(2, seriesId = 5L, formats = listOf("epub")), targetNull, "Same series") shouldBe
                "EPUB"
            candidateTags(candidateNull, targetNull, "Same series") shouldBe "EPUB"
        }
    }

    describe("candidateMeta") {

        it("#120 TC-5: author and year, joined with the separator") {
            candidateMeta(mergeTestBook(2, author = "Ann", published = "1999-01-01")) shouldBe "Ann · 1999"
        }

        it("#120 TC-5: no year leaves the author alone, with no trailing separator") {
            candidateMeta(mergeTestBook(2, author = "Ann", published = null)) shouldBe "Ann"
        }

        it("#120 TC-5: no creators shows the unknown-author text") {
            candidateMeta(mergeTestBook(2, author = null, published = null)) shouldBe "Unknown author"
        }
    }

    describe("mergeNote") {

        it("#120 TC-6: each state gives its note, and a blank query wins over every other state") {
            mergeNote("", searching = false, candidateCount = 0, isOnline = true, searchFailed = false) shouldBe
                MergeNote.Idle
            mergeNote("   ", searching = true, candidateCount = 2, isOnline = false, searchFailed = true) shouldBe
                MergeNote.Idle
            mergeNote("   ", searching = false, candidateCount = 0, isOnline = false, searchFailed = false) shouldBe
                MergeNote.Idle
            mergeNote("dune", searching = false, candidateCount = 0, isOnline = true, searchFailed = true) shouldBe
                MergeNote.Failed
            mergeNote("dune", searching = false, candidateCount = 0, isOnline = false, searchFailed = false) shouldBe
                MergeNote.Offline
            mergeNote("dune", searching = true, candidateCount = 0, isOnline = true, searchFailed = false) shouldBe
                MergeNote.Searching
            mergeNote("dune", searching = false, candidateCount = 1, isOnline = true, searchFailed = false) shouldBe
                MergeNote.Count(1)
            mergeNote("dune", searching = false, candidateCount = 3, isOnline = true, searchFailed = false) shouldBe
                MergeNote.Count(3)
            mergeNote("  zzz ", searching = false, candidateCount = 0, isOnline = true, searchFailed = false) shouldBe
                MergeNote.NoMatch("zzz")
        }

        it("#120 TC-6: failed beats offline, and offline beats searching") {
            mergeNote("dune", searching = true, candidateCount = 0, isOnline = false, searchFailed = true) shouldBe
                MergeNote.Failed
            mergeNote("dune", searching = true, candidateCount = 0, isOnline = false, searchFailed = false) shouldBe
                MergeNote.Offline
        }
    }

    describe("acceptsQuery") {

        it("#120 TC-7: the cap is 1024 UTF-8 bytes, not characters") {
            acceptsQuery("") shouldBe true
            acceptsQuery("a".repeat(1024)) shouldBe true
            acceptsQuery("a".repeat(1025)) shouldBe false
            acceptsQuery("é".repeat(512)) shouldBe true
            acceptsQuery("é".repeat(513)) shouldBe false
            acceptsQuery("€".repeat(342)) shouldBe false
        }
    }

    describe("undoFailureIsTerminal") {

        it("#120 TC-8: only a 404 and a 409 end the undo") {
            val terminal: Map<MergeError, Boolean> = mapOf(
                MergeError.Unreachable to false,
                MergeError.UnexpectedResponse to false,
                MergeError.SessionExpired to false,
                MergeError.Forbidden to false,
                MergeError.NotFound to true,
                MergeError.Conflict(null) to true,
                MergeError.Http(500, null) to false,
            )

            terminal.forEach { (error, expected) -> undoFailureIsTerminal(error) shouldBe expected }
        }
    }
})
