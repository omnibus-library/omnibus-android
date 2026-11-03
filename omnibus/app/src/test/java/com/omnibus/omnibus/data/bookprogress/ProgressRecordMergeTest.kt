package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.data.progress.ReadingPosition
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ProgressRecordMergeTest : DescribeSpec({

    describe("merging the device position with the server records") {

        it("TC-49: merge with one side, and order Reading first") {
            val serverReading = readingRecord(40.0, 100, cfi = "server")
            val serverListening = listeningRecord(60.0, 90, totalSeconds = 600.0)

            mergeProgressRecords(null, listOf(serverReading, serverListening)) shouldBe listOf(serverReading, serverListening)

            mergeProgressRecords(ReadingPosition("book-1", "x", 50, 200), emptyList()) shouldBe
                listOf(readingRecord(50.0, 200, cfi = "x"))

            mergeProgressRecords(null, emptyList()) shouldBe emptyList()

            mergeProgressRecords(ReadingPosition("book-1", "x", 50, 200), listOf(serverListening)) shouldBe
                listOf(readingRecord(50.0, 200, cfi = "x"), serverListening)
        }

        it("TC-50: the newest reading record wins, and a tie keeps the server") {
            val localNewer = mergeProgressRecords(ReadingPosition("book-1", "x", 50, 200), listOf(readingRecord(40.0, 100)))
            localNewer shouldBe listOf(readingRecord(50.0, 200, cfi = "x"))

            val serverNewer = mergeProgressRecords(ReadingPosition("book-1", "x", 50, 100), listOf(readingRecord(40.0, 200, cfi = "y")))
            serverNewer shouldBe listOf(readingRecord(40.0, 200, cfi = "y"))

            val resolved = ResolvedPosition(3, null, null, null, PositionConfidence.High)
            val serverRecord = readingRecord(40.0, 200, cfi = "y", resolved = resolved)
            val tie = mergeProgressRecords(ReadingPosition("book-1", "x", 50, 200), listOf(serverRecord))
            tie shouldBe listOf(serverRecord)
        }
    }
})
