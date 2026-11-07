package com.omnibus.omnibus.feature.audiobook.player.offers

import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackError
import com.omnibus.omnibus.testing.randomDouble
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PlayerBannerTest : DescribeSpec({

    describe("choosing the banner") {
        context("with a playback error") {
            // P-SYNC-8: the playback error wins over both offers
            it("#166 TC-66: shows the error over both offers (P-SYNC-8)") {
                val error = randomPlaybackError()
                val offers = ResumeOffersUIState(deviceSeconds = randomDouble(), ebook = randomEbookOfferUIState())

                playerBanner(error, offers) shouldBe PlayerBanner.Error(error)
            }
        }

        context("without a playback error") {
            // P-SYNC-8: the device offer beats the ebook offer
            it("#166 TC-67: shows the device offer over the ebook offer (P-SYNC-8)") {
                val seconds = randomDouble()
                val offers = ResumeOffersUIState(deviceSeconds = seconds, ebook = randomEbookOfferUIState())

                playerBanner(null, offers) shouldBe PlayerBanner.DeviceOffer(seconds)
            }

            // R10: the payloads carry the seconds and the direction through
            it("#166 TC-69: carries an ebook offer's seconds with its source direction (R10)") {
                val ebook = randomEbookOfferUIState()

                playerBanner(null, ResumeOffersUIState(ebook = ebook)) shouldBe
                    PlayerBanner.EbookOffer(ebook.seconds, ebook.sourceAhead)
            }

            // P-SYNC-8: an empty state shows nothing
            it("#166 TC-67: shows nothing with no offers (P-SYNC-8)") {
                playerBanner(null, ResumeOffersUIState()).shouldBeNull()
            }
        }
    }
})
