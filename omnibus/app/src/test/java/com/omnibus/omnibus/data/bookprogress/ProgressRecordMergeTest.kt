package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

private fun devicePosition(uuid: String, clock: Long, cfi: String, percent: Int) =
    ReadingPosition(uuid, cfi = cfi, percent = percent, clientUpdatedAt = clock)

class ProgressRecordMergeTest : DescribeSpec({

    describe("merging the device position with the server records") {
        context("when there is no device position") {
            context("when the server has no records") {
                it("TC-49: gives no records") {
                    mergeProgressRecords(null, emptyList()).shouldBeEmpty()
                }
            }

            context("when the server has both records") {
                it("TC-49: gives them Reading first") {
                    val uuid = randomString()
                    val reading = readingRecord(randomDouble(0.0, 100.0), randomLong(), uuid = uuid)
                    val listening = listeningRecord(randomDouble(0.0, 100.0), randomLong(), uuid = uuid)

                    mergeProgressRecords(null, listOf(listening, reading)) shouldBe listOf(reading, listening)
                }
            }
        }

        context("when there is a device position") {
            context("when the server has no Reading record") {
                it("TC-49: gives the device position first") {
                    val uuid = randomString()
                    val cfi = randomString()
                    val percent = randomInt(0..100)
                    val clock = randomLong()
                    val listening = listeningRecord(randomDouble(0.0, 100.0), randomLong(), uuid = uuid)

                    mergeProgressRecords(devicePosition(uuid, clock, cfi, percent), listOf(listening)) shouldBe
                        listOf(readingRecord(percent.toDouble(), clock, cfi = cfi, uuid = uuid), listening)
                }
            }

            context("when the device position is newer than the server's Reading record") {
                it("TC-50: gives the device position") {
                    val uuid = randomString()
                    val cfi = randomString()
                    val percent = randomInt(0..100)
                    val clock = randomLong()
                    val server = readingRecord(randomDouble(0.0, 100.0), clock - 1, uuid = uuid)

                    mergeProgressRecords(devicePosition(uuid, clock, cfi, percent), listOf(server)) shouldBe
                        listOf(readingRecord(percent.toDouble(), clock, cfi = cfi, uuid = uuid))
                }
            }

            context("when the server's Reading record is newer than the device position") {
                it("TC-50: gives the server's record") {
                    val uuid = randomString()
                    val clock = randomLong()
                    val server = readingRecord(randomDouble(0.0, 100.0), clock + 1, uuid = uuid)
                    val device = devicePosition(uuid, clock, randomString(), randomInt(0..100))

                    mergeProgressRecords(device, listOf(server)) shouldBe listOf(server)
                }
            }

            context("when both Reading positions have the same clock") {
                it("TC-50: gives the server's record, which carries the resolved position") {
                    val uuid = randomString()
                    val clock = randomLong()
                    val resolved = ResolvedPosition(randomInt(), null, null, null, PositionConfidence.High)
                    val server = readingRecord(randomDouble(0.0, 100.0), clock, resolved = resolved, uuid = uuid)
                    val device = devicePosition(uuid, clock, randomString(), randomInt(0..100))

                    mergeProgressRecords(device, listOf(server)) shouldBe listOf(server)
                }
            }
        }
    }
})
