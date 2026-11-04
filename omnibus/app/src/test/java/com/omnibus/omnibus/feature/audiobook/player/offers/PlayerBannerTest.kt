package com.omnibus.omnibus.feature.audiobook.player.offers

import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PlayerBannerTest : DescribeSpec({

    val both = ResumeOffersUIState(deviceSeconds = 3725.0, ebook = EbookOfferUIState(65.0, sourceAhead = true))
    val deviceOnly = ResumeOffersUIState(deviceSeconds = 3725.0)
    val ebookOnly = ResumeOffersUIState(ebook = EbookOfferUIState(65.0, sourceAhead = true))

    describe("priority") {

        // P-SYNC-8: the playback error wins over both offers
        it("#166 TC-66: an error beats both offers (P-SYNC-8)") {
            playerBanner(PlaybackError.StoppedAfterRetries, both) shouldBe
                PlayerBanner.Error(PlaybackError.StoppedAfterRetries)
        }

        // P-SYNC-8: the device offer beats the ebook offer, and an empty state shows nothing
        it("#166 TC-67: with no error the device offer comes first, then the ebook, then nothing (P-SYNC-8)") {
            playerBanner(null, both) shouldBe PlayerBanner.DeviceOffer(3725.0)
            playerBanner(null, deviceOnly) shouldBe PlayerBanner.DeviceOffer(3725.0)
            playerBanner(null, ebookOnly) shouldBe PlayerBanner.EbookOffer(65.0, sourceAhead = true)
            playerBanner(null, ResumeOffersUIState()) shouldBe null
        }

        // P-SYNC-8: a held-back offer shows once the banner above it goes
        it("#166 TC-68: a held-back offer shows once the higher banner goes (P-SYNC-8)") {
            playerBanner(PlaybackError.PartUnreadable, ebookOnly) shouldBe
                PlayerBanner.Error(PlaybackError.PartUnreadable)
            playerBanner(null, ebookOnly) shouldBe PlayerBanner.EbookOffer(65.0, sourceAhead = true)

            playerBanner(null, both) shouldBe PlayerBanner.DeviceOffer(3725.0)
            playerBanner(null, ebookOnly) shouldBe PlayerBanner.EbookOffer(65.0, sourceAhead = true)
        }

        // R10: the payloads carry the seconds and the direction through
        it("#166 TC-69: the device and ebook banners carry their seconds and the source direction (R10)") {
            playerBanner(null, deviceOnly) shouldBe PlayerBanner.DeviceOffer(seconds = 3725.0)
            playerBanner(null, ResumeOffersUIState(ebook = EbookOfferUIState(65.0, sourceAhead = false))) shouldBe
                PlayerBanner.EbookOffer(seconds = 65.0, sourceAhead = false)
        }
    }
})
