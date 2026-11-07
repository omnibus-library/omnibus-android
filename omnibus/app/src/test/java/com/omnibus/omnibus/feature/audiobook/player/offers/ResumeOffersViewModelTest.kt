package com.omnibus.omnibus.feature.audiobook.player.offers

import com.omnibus.omnibus.data.audiobook.offers.TestResumeOffers
import com.omnibus.omnibus.data.audiobook.offers.TestResumeOffers.Call
import com.omnibus.omnibus.data.audiobook.offers.VisibleOffers
import com.omnibus.omnibus.data.audiobook.offers.randomEbookOffer
import com.omnibus.omnibus.data.audiobook.position.randomLatePosition
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ResumeOffersViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(bookUuid: String = randomString(), offers: TestResumeOffers = TestResumeOffers()) =
        ResumeOffersViewModel(bookUuid, offers)

    describe("the offers shown") {
        context("when they are for another book") {
            // P-SYNC-6, P-SYNC-7: a player shows only its own book's offers
            it("#166 TC-61: shows nothing (P-SYNC-6, P-SYNC-7)") {
                val uuid = randomString()
                val offers = TestResumeOffers()
                val vm = sut(uuid, offers)

                offers.offers.value = VisibleOffers(
                    device = randomLatePosition().copy(bookUuid = "$uuid-other"),
                    ebook = randomEbookOffer().copy(bookUuid = "$uuid-other"),
                )

                vm.uiState.value shouldBe ResumeOffersUIState()
            }
        }

        context("when they are for this book") {
            // P-SYNC-7, P-SYNC-AC5: the offers' positions and direction map to the banner state
            it("#166 TC-62: maps the seconds with the source direction (P-SYNC-7, P-SYNC-AC5)") {
                val uuid = randomString()
                val device = randomLatePosition().copy(bookUuid = uuid)
                val ebook = randomEbookOffer().copy(bookUuid = uuid)
                val offers = TestResumeOffers()
                val vm = sut(uuid, offers)

                offers.offers.value = VisibleOffers(device = device, ebook = ebook)

                vm.uiState.value shouldBe ResumeOffersUIState(
                    deviceSeconds = device.positionSeconds,
                    ebook = EbookOfferUIState(ebook.positionSeconds, ebook.sourceAhead),
                )
            }
        }
    }

    describe("acting on an offer") {
        // R2, R3: Go and Dismiss on the device offer hand the offer back to the holder
        it("#166 TC-63: forwards Go then Dismiss on the device offer to the holder (R2, R3)") {
            val uuid = randomString()
            val device = randomLatePosition().copy(bookUuid = uuid)
            val offers = TestResumeOffers()
            val vm = sut(uuid, offers)
            offers.offers.value = VisibleOffers(device = device)

            vm.onGoDevice()
            vm.onDismissDevice()

            offers.calls shouldBe listOf(Call.AcceptDevice(device), Call.DismissDevice(device))
        }

        // R7, R8: Go and Dismiss on the ebook offer hand the offer back to the holder
        it("#166 TC-64: forwards Go then Dismiss on the ebook offer to the holder (R7, R8)") {
            val uuid = randomString()
            val ebook = randomEbookOffer().copy(bookUuid = uuid)
            val offers = TestResumeOffers()
            val vm = sut(uuid, offers)
            offers.offers.value = VisibleOffers(ebook = ebook)

            vm.onGoEbook()
            vm.onDismissEbook()

            offers.calls shouldBe listOf(Call.AcceptEbook(ebook), Call.DismissEbook(ebook))
        }

        context("when no offer is showing") {
            // R2, R7: an intent with no offer forwards nothing
            it("#166 TC-65: forwards nothing (R2, R7)") {
                val offers = TestResumeOffers()
                val vm = sut(offers = offers)

                vm.onGoDevice()
                vm.onDismissDevice()
                vm.onGoEbook()
                vm.onDismissEbook()

                offers.calls.shouldBeEmpty()
            }
        }
    }
})
