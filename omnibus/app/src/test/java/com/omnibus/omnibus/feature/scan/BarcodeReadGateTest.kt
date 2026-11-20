package com.omnibus.omnibus.feature.scan

import com.omnibus.omnibus.data.scan.isbn13WithCheckDigit
import com.omnibus.omnibus.data.scan.randomDigits
import com.omnibus.omnibus.data.scan.randomValidIsbn13
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BarcodeReadGateTest : DescribeSpec({

    describe("reading barcodes frame by frame") {

        it("#317 TC-9: looks a book barcode up once while it stays in frame") {
            val gate = BarcodeReadGate()
            val isbn = randomValidIsbn13()
            // Frames every third of the grace period, so the barcode is seen on every other frame and never goes
            // unseen for the full grace period.
            val frameMillis = GRACE_MILLIS / 3

            val events = (0..4).flatMap { frame ->
                gate.onFrame(if (frame % 2 == 0) listOf(isbn) else emptyList(), frame * frameMillis)
            }

            events shouldBe listOf(GateEvent.Lookup(isbn))
        }

        context("when a book barcode leaves the frame") {

            it("#317 TC-10: looks it up again once it has been absent for the full grace period") {
                val gate = BarcodeReadGate()
                val isbn = randomValidIsbn13()
                gate.onFrame(listOf(isbn), 0)

                gate.onFrame(listOf(isbn), GRACE_MILLIS) shouldBe listOf(GateEvent.Lookup(isbn))
            }

            it("#317 TC-10: does not look it up again one millisecond before the grace period ends") {
                val gate = BarcodeReadGate()
                val isbn = randomValidIsbn13()
                gate.onFrame(listOf(isbn), 0)

                gate.onFrame(listOf(isbn), GRACE_MILLIS - 1) shouldBe emptyList()
            }
        }

        context("when a barcode that is not a book's ISBN is read") {

            it("#317 TC-11: rejects it when it first arrives in the frame") {
                val gate = BarcodeReadGate()
                val value = isbn13WithCheckDigit("0" + randomDigits(11))

                gate.onFrame(listOf(value), 0) shouldBe listOf(GateEvent.Rejected(value, announce = true))
            }

            it("#317 TC-11: does not reject it again while it stays in frame") {
                val gate = BarcodeReadGate()
                val value = isbn13WithCheckDigit("0" + randomDigits(11))
                gate.onFrame(listOf(value), 0)

                gate.onFrame(listOf(value), GRACE_MILLIS - 1) shouldBe emptyList()
            }

            it("#317 TC-11: rejects it again when it returns after leaving the frame, without announcing it") {
                val gate = BarcodeReadGate()
                val value = isbn13WithCheckDigit("0" + randomDigits(11))
                gate.onFrame(listOf(value), 0)

                gate.onFrame(listOf(value), GRACE_MILLIS) shouldBe listOf(GateEvent.Rejected(value, announce = false))
            }

            it("#317 TC-11: announces a different non-book barcode the first time it is seen") {
                val gate = BarcodeReadGate()
                val first = isbn13WithCheckDigit("0" + randomDigits(11))
                val second = isbn13WithCheckDigit("1" + randomDigits(11))
                gate.onFrame(listOf(first), 0)

                gate.onFrame(listOf(second), 1) shouldBe listOf(GateEvent.Rejected(second, announce = true))
            }
        }
    }
})
