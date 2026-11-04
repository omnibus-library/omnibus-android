package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.position.ResumeRules.ResumeChoice
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** The pure resume rules (R5, R7, R9): tested without coroutines, one table per rule. */
class ResumeRulesTest : DescribeSpec({

    describe("orderingClock") {
        // TC-30: client_updated_at, else updated_at (I-RES-1)
        it("TC-30: client_updated_at, else updated_at (I-RES-1)") {
            ResumeRules.orderingClock(5, 9) shouldBe 5L
            ResumeRules.orderingClock(null, 9) shouldBe 9L
        }
    }

    describe("newer") {
        // TC-31: the server wins only when strictly newer and positive (I-RES-1)
        it("TC-31: the server wins only when strictly newer and positive (I-RES-1)") {
            val local = AudioPosition("book-1", 100.0, 7, 10)
            val server = ServerAudioPosition(200.0, 7, 11)
            val localChoice = ResumeChoice(100.0, 7)
            val serverChoice = ResumeChoice(200.0, 7)
            data class Row(val name: String, val local: AudioPosition?, val server: ServerAudioPosition?, val expected: ResumeChoice?)
            listOf(
                Row("server clock 11 vs local 10", local, server, serverChoice),
                Row("server clock 10 vs local 10", local, server.copy(orderingClock = 10), localChoice),
                Row("server clock 9 vs local 10", local, server.copy(orderingClock = 9), localChoice),
                Row("server only", null, server, serverChoice),
                Row("local only", local, null, localChoice),
                Row("neither", null, null, null),
                Row("server position 0 with clock 11", local, server.copy(positionSeconds = 0.0), localChoice),
            ).forEach { row ->
                withClue(row.name) {
                    ResumeRules.newer(row.local, row.server) shouldBe row.expected
                }
            }
        }
    }

    describe("fileMatches") {
        // TC-32: same file, a null record file or loaded file 0 match (I-RES-3)
        it("TC-32: same file, a null record file or loaded file 0 match (I-RES-3)") {
            listOf(
                Triple<Long?, Long, Boolean>(7L, 7L, true),
                Triple(7L, 8L, false),
                Triple(null, 8L, true),
                Triple(7L, 0L, true),
            ).forEach { (recordFileId, loaded, expected) ->
                withClue("record $recordFileId, loaded $loaded") {
                    ResumeRules.fileMatches(recordFileId, loaded) shouldBe expected
                }
            }
        }
    }

    describe("startFor") {
        // TC-33: the 1 s floor is exclusive and a mismatch starts at 0 (I-RES-3)
        it("TC-33: the 1 s floor is exclusive and a mismatch starts at 0 (I-RES-3)") {
            listOf(
                Triple(ResumeChoice(1.0, 7), 7L, null),
                Triple(ResumeChoice(1.01, 7), 7L, 1.01),
                Triple(ResumeChoice(600.0, 8), 7L, null),
                Triple(null, 7L, null),
            ).forEach { (choice, loaded, expected) ->
                withClue("choice $choice, loaded $loaded") {
                    ResumeRules.startFor(choice, loaded) shouldBe expected
                }
            }
        }
    }

    describe("isLateOffer") {
        // TC-34: offered only when newer, same file and more than 1 s ahead (I-RES-6)
        it("TC-34: offered only when newer, same file and more than 1 s ahead (I-RES-6)") {
            val local = AudioPosition("book-1", 30.0, 7, 10)
            data class Row(val name: String, val late: ServerAudioPosition, val local: AudioPosition?, val expected: Boolean)
            listOf(
                Row("31.0 at clock 11 file 7", ServerAudioPosition(31.0, 7, 11), local, false),
                Row("31.01 at clock 11 file 7", ServerAudioPosition(31.01, 7, 11), local, true),
                Row("60 at clock 11 file 8", ServerAudioPosition(60.0, 8, 11), local, false),
                Row("60 at clock 10 file 7", ServerAudioPosition(60.0, 7, 10), local, false),
                Row("60 at clock 9 file 7", ServerAudioPosition(60.0, 7, 9), local, false),
                Row("60 at clock 11 file null, no local", ServerAudioPosition(60.0, null, 11), null, true),
            ).forEach { row ->
                withClue(row.name) {
                    ResumeRules.isLateOffer(row.late, row.local, 7L, 30.0) shouldBe row.expected
                }
            }
        }
    }
})
