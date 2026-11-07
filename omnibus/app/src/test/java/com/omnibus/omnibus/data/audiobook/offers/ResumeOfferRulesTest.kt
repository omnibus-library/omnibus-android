package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.audiobook.position.randomLatePosition
import com.omnibus.omnibus.testing.randomDouble
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ResumeOfferRulesTest : DescribeSpec({

    describe("the device offer") {

        // I-RES-6, P-SYNC-6, P-SYNC-AC4: the threshold is strictly more than 1 s
        it("#166 TC-25, TC-26: shows a matching position more than 1 s ahead (I-RES-6, P-SYNC-AC4)") {
            val book = randomPlayingBook()
            val position = randomDouble(0.0, 3_000.0)
            val late = randomLatePosition().copy(book.bookUuid, book.bookFileId, positionSeconds = position + 1.01)

            visibleOffers(playbackState(book, positionSeconds = position), late, OfferMemory()).device shouldBe late
        }

        // I-RES-6, P-SYNC-AC4: playing or seeking past the offer hides it live
        it("#166 TC-26, TC-27: hides a position at most 1 s ahead of the live one, for each lead (I-RES-6, P-SYNC-AC4)") {
            val book = randomPlayingBook()
            val late = randomLatePosition().copy(book.bookUuid, book.bookFileId, positionSeconds = 100.0)

            checkAll(Exhaustive.of(99.0, 100.0, 150.0)) { position ->
                visibleOffers(playbackState(book, positionSeconds = position), late, OfferMemory()).device.shouldBeNull()
            }
        }

        // I-RES-6
        it("#166 TC-30: hides a position saved for another book or file (I-RES-6)") {
            val book = randomPlayingBook()
            val late = randomLatePosition().copy(positionSeconds = 100.0)

            checkAll(
                Exhaustive.of(
                    late.copy(bookUuid = "${book.bookUuid}-other", bookFileId = book.bookFileId),
                    late.copy(bookUuid = book.bookUuid, bookFileId = book.bookFileId + 1),
                ),
            ) { other ->
                visibleOffers(playbackState(book, positionSeconds = 0.0), other, OfferMemory()).device.shouldBeNull()
            }
        }

        context("when the listener dismissed an offer") {
            // R3, P-SYNC-AC4
            it("#166 TC-32: hides that offer (spec §3)") {
                val book = randomPlayingBook()
                val late = randomLatePosition().copy(book.bookUuid, book.bookFileId, positionSeconds = 100.0)

                visibleOffers(playbackState(book, positionSeconds = 0.0), late, OfferMemory(dismissedDevice = late))
                    .device.shouldBeNull()
            }

            // R3, P-SYNC-AC4
            it("#166 TC-32: shows a different position (spec §3)") {
                val book = randomPlayingBook()
                val late = randomLatePosition().copy(book.bookUuid, book.bookFileId, positionSeconds = 100.0)
                val newer = late.copy(positionSeconds = 200.0)

                visibleOffers(playbackState(book, positionSeconds = 0.0), newer, OfferMemory(dismissedDevice = late))
                    .device shouldBe newer
            }
        }
    }

    describe("the ebook offer") {

        // P-SYNC-7, P-SYNC-AC5
        it("#166 TC-31: shows for the loaded file of the loaded book (P-SYNC-7, P-SYNC-AC5)") {
            val book = randomPlayingBook()
            val offer = randomEbookOffer().copy(bookUuid = book.bookUuid, bookFileId = book.bookFileId)

            visibleOffers(playbackState(book), null, OfferMemory(ebook = offer)).ebook shouldBe offer
        }

        // P-SYNC-7, P-SYNC-AC5
        it("#166 TC-31: hides an offer for another book or file (P-SYNC-7, P-SYNC-AC5)") {
            val book = randomPlayingBook()
            val offer = randomEbookOffer()

            checkAll(
                Exhaustive.of(
                    offer.copy(bookUuid = "${book.bookUuid}-other", bookFileId = book.bookFileId),
                    offer.copy(bookUuid = book.bookUuid, bookFileId = book.bookFileId + 1),
                ),
            ) { other ->
                visibleOffers(playbackState(book), null, OfferMemory(ebook = other)).ebook.shouldBeNull()
            }
        }
    }

    describe("both offers together") {

        // I-RES-6, P-SYNC-7: nothing shows with no settled book, whatever qualifies
        it("#166 TC-28, TC-29: show nothing while loading or with no book (I-RES-6, P-SYNC-7)") {
            val book = randomPlayingBook()
            val late = randomLatePosition().copy(book.bookUuid, book.bookFileId, positionSeconds = 100.0)
            val offer = randomEbookOffer().copy(bookUuid = book.bookUuid, bookFileId = book.bookFileId)

            checkAll(Exhaustive.of(playbackState(book, isLoading = true), playbackState(book = null))) { playback ->
                visibleOffers(playback, late, OfferMemory(ebook = offer)) shouldBe VisibleOffers()
            }
        }
    }
})
