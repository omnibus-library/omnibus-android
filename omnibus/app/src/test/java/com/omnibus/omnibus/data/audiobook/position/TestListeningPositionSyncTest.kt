package com.omnibus.omnibus.data.audiobook.position

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

/** The shared fake that #166 relies on: a settable offer and a forceSave counter. */
class TestListeningPositionSyncTest : DescribeSpec({

    describe("TestListeningPositionSync") {
        // TC-79: a settable offer and a forceSave counter (C8)
        it("TC-79: a settable offer and a forceSave counter (C8)") {
            runTest {
                val fake = TestListeningPositionSync()
                fake.lateServerPosition.value shouldBe null

                val offer = LatePosition("book-1", 11L, 120.0)
                fake.lateServerPosition.value = offer
                fake.lateServerPosition.value shouldBe offer

                repeat(3) { fake.forceSave() }
                fake.forceSaveCount shouldBe 3
            }
        }
    }
})
