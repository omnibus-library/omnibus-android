package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.position.LatePosition
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ResumeOfferRulesTest : DescribeSpec({

    val late = LatePosition("book-1", 11L, 1800.0)
    val ebook = EbookOffer("book-1", 11L, 65.0, sourceAhead = true, sourceClock = 500L)

    describe("device offer") {

        // I-RES-6, P-SYNC-6: a matching position more than 1 s ahead shows
        it("#166 TC-25: a matching late position more than 1 s ahead is the device offer (I-RES-6, P-SYNC-6)") {
            visibleOffers(playbackState(positionSeconds = 0.0), late, OfferMemory()) shouldBe
                VisibleOffers(device = late)
        }

        // P-SYNC-AC4: the threshold is strictly more than 1 s
        it("#166 TC-26: shows only when more than 1 s ahead of the live position (P-SYNC-AC4)") {
            val playback = playbackState(positionSeconds = 100.0)
            val rows = listOf(
                LatePosition("book-1", 11L, 101.0) to null,
                LatePosition("book-1", 11L, 101.01) to LatePosition("book-1", 11L, 101.01),
                LatePosition("book-1", 11L, 100.0) to null,
                LatePosition("book-1", 11L, 50.0) to null,
            )
            rows.forEach { (position, expected) ->
                visibleOffers(playback, position, OfferMemory()).device shouldBe expected
            }
        }

        // I-RES-6, P-SYNC-6: playing or seeking past the offer hides it live
        it("#166 TC-27: the device offer goes once the listener plays or seeks past it (I-RES-6)") {
            visibleOffers(playbackState(positionSeconds = 0.0), late, OfferMemory()).device shouldBe late
            visibleOffers(playbackState(positionSeconds = 3725.0), late, OfferMemory()).device shouldBe null
        }

        // I-RES-6: an offer never shows while the controller is loading
        it("#166 TC-28: nothing shows while loading, even with a device and an ebook offer (I-RES-6, P-SYNC-7)") {
            val memory = OfferMemory(ebook = ebook)

            visibleOffers(playbackState(isLoading = true), late, memory) shouldBe VisibleOffers()
        }

        // I-RES-6: nothing shows with no book loaded
        it("#166 TC-29: nothing shows when no book is loaded (I-RES-6, P-SYNC-7)") {
            val memory = OfferMemory(ebook = ebook)

            visibleOffers(playbackState(book = null), late, memory) shouldBe VisibleOffers()
        }

        // I-RES-6: a position for another book or another file is never offered
        it("#166 TC-30: a late position for another book or another file is hidden (I-RES-6)") {
            val playback = playbackState(positionSeconds = 0.0)

            visibleOffers(playback, late.copy(bookUuid = "other-book"), OfferMemory()).device shouldBe null
            visibleOffers(playback, late.copy(bookFileId = 12L), OfferMemory()).device shouldBe null
        }

        // R3, P-SYNC-AC4: a dismissed device offer stays hidden, a different one shows
        it("#166 TC-32: a dismissed device offer stays hidden, a new position shows again (spec §3)") {
            val playback = playbackState(positionSeconds = 0.0)

            visibleOffers(playback, late, OfferMemory(dismissedDevice = late)).device shouldBe null
            visibleOffers(playback, late.copy(positionSeconds = 2000.0), OfferMemory(dismissedDevice = late))
                .device shouldBe late.copy(positionSeconds = 2000.0)
        }
    }

    describe("ebook offer") {

        // P-SYNC-7, P-SYNC-AC5: the memory's ebook offer shows for its own book and file only
        it("#166 TC-31: the ebook offer shows for the loaded book and file only (P-SYNC-7, P-SYNC-AC5)") {
            val playback = playbackState(positionSeconds = 0.0)

            visibleOffers(playback, null, OfferMemory(ebook = ebook)).ebook shouldBe ebook
            visibleOffers(playback, null, OfferMemory(ebook = ebook.copy(bookUuid = "other-book"))).ebook shouldBe null
            visibleOffers(playback, null, OfferMemory(ebook = ebook.copy(bookFileId = 12L))).ebook shouldBe null
        }
    }

    describe("both offers") {

        // P-SYNC-8: both qualify, and the rules report both (the priority is the UI's)
        it("#166 TC-33: a device and an ebook offer both qualify and both are reported (P-SYNC-8)") {
            visibleOffers(playbackState(positionSeconds = 0.0), late, OfferMemory(ebook = ebook)) shouldBe
                VisibleOffers(device = late, ebook = ebook)
        }
    }
})
