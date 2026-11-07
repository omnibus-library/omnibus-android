package com.omnibus.omnibus.feature.bookdetail.more.merge

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.merge.MergeError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun candidate(seriesId: Long?, formats: List<String>): Book = randomBook().copy(seriesId = seriesId, formats = formats)

private fun target(seriesId: Long?): Book = randomBook().copy(seriesId = seriesId)

class MergeRulesTest : DescribeSpec({

    describe("the Merge with row") {
        it("#120 TC-1: is hidden, for a user who is unknown or not an admin, or a book without files") {
            checkAll(
                Exhaustive.of(Triple(null, true, true), Triple(false, true, true), Triple(true, false, true)),
            ) { (admin, files, online) ->
                mergeRow(admin, files, online) shouldBe MergeRow.Hidden
            }
        }

        context("when an admin views a book with files") {
            it("#120 TC-1: is enabled online, disabled offline") {
                mergeRow(isAdmin = true, hasFiles = true, isOnline = true) shouldBe MergeRow.Enabled
                mergeRow(isAdmin = true, hasFiles = true, isOnline = false) shouldBe MergeRow.DisabledOffline
            }
        }
    }

    describe("a candidate's tags") {
        it("#120 TC-3, TC-4: name a shared non-null series, then the upper-cased formats") {
            checkAll(
                Exhaustive.of(
                    Triple(candidate(5L, listOf("epub", "m4B")), target(5L), "Same series · EPUB, M4B"),
                    Triple(candidate(5L, emptyList()), target(5L), "Same series"),
                    Triple(candidate(6L, listOf("epub")), target(5L), "EPUB"),
                    Triple(candidate(null, listOf("epub")), target(5L), "EPUB"),
                    Triple(candidate(5L, listOf("epub")), target(null), "EPUB"),
                    Triple(candidate(null, listOf("epub")), target(null), "EPUB"),
                    Triple(candidate(null, emptyList()), target(null), ""),
                ),
            ) { (candidate, target, expected) ->
                candidateTags(candidate, target, "Same series") shouldBe expected
            }
        }
    }

    describe("a candidate's meta line") {
        it("#120 TC-5: is the author with any year") {
            val ann = listOf(Creator("Ann", null, null, null))

            checkAll(
                Exhaustive.of(
                    Triple(ann, "1999-01-01", "Ann · 1999"),
                    Triple(ann, null, "Ann"),
                    Triple(emptyList<Creator>(), null, "Unknown author"),
                ),
            ) { (creators, published, expected) ->
                candidateMeta(randomBook().copy(creators = creators, publishedDate = published)) shouldBe expected
            }
        }
    }

    describe("the note under the search field") {
        it("#120 TC-6: takes blank, failed, offline, searching, then the results, in that order") {
            checkAll(
                Exhaustive.of(
                    "   " to MergeNote.Idle,
                    "dune" to MergeNote.Failed,
                ),
            ) { (query, expected) ->
                mergeNote(query, searching = true, candidateCount = 2, isOnline = false, searchFailed = true) shouldBe expected
            }

            mergeNote("dune", searching = true, candidateCount = 2, isOnline = false, searchFailed = false) shouldBe
                MergeNote.Offline
            mergeNote("dune", searching = true, candidateCount = 2, isOnline = true, searchFailed = false) shouldBe
                MergeNote.Searching
            mergeNote("dune", searching = false, candidateCount = 3, isOnline = true, searchFailed = false) shouldBe
                MergeNote.Count(3)
            mergeNote("  zzz ", searching = false, candidateCount = 0, isOnline = true, searchFailed = false) shouldBe
                MergeNote.NoMatch("zzz")
        }
    }

    describe("the query cap") {
        it("#120 TC-7: is 1024 UTF-8 bytes, not characters") {
            checkAll(
                Exhaustive.of(
                    "a".repeat(1024) to true,
                    "a".repeat(1025) to false,
                    "é".repeat(512) to true,
                    "é".repeat(513) to false,
                ),
            ) { (query, accepted) ->
                acceptsQuery(query) shouldBe accepted
            }
        }
    }

    describe("a failed undo") {
        it("#120 TC-8: is terminal only for a 404 or a 409") {
            checkAll(
                Exhaustive.of(
                    MergeError.NotFound to true,
                    MergeError.Conflict(null) to true,
                    MergeError.Unreachable to false,
                    MergeError.UnexpectedResponse to false,
                    MergeError.SessionExpired to false,
                    MergeError.Forbidden to false,
                    MergeError.Http(500, null) to false,
                ),
            ) { (error, terminal) ->
                undoFailureIsTerminal(error) shouldBe terminal
            }
        }
    }
})
