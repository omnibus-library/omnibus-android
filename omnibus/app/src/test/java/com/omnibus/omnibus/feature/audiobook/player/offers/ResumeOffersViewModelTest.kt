package com.omnibus.omnibus.feature.audiobook.player.offers

import com.omnibus.omnibus.data.audiobook.offers.EbookOffer
import com.omnibus.omnibus.data.audiobook.offers.FakeResumeOffers
import com.omnibus.omnibus.data.audiobook.offers.FakeResumeOffers.Call
import com.omnibus.omnibus.data.audiobook.offers.VisibleOffers
import com.omnibus.omnibus.data.audiobook.position.LatePosition
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val BOOK = "book-1"

@OptIn(ExperimentalCoroutinesApi::class)
class ResumeOffersViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val device = LatePosition(BOOK, 11L, 300.0)
    val ebook = EbookOffer(BOOK, 11L, 65.0, sourceAhead = true, sourceClock = 500L)

    describe("offers for this book") {

        // P-SYNC-6, P-SYNC-7: a player shows only its own book's offers
        it("#166 TC-61: offers for another book never show, and this book's offers map through (P-SYNC-6, P-SYNC-7)") {
            val fake = FakeResumeOffers()
            val vm = ResumeOffersViewModel(BOOK, fake)

            fake.offers.value = VisibleOffers(
                device = device.copy(bookUuid = "other-book"),
                ebook = ebook.copy(bookUuid = "other-book"),
            )
            vm.uiState.value shouldBe ResumeOffersUIState()

            fake.offers.value = VisibleOffers(device = device, ebook = ebook)
            vm.uiState.value shouldBe ResumeOffersUIState(deviceSeconds = 300.0, ebook = EbookOfferUIState(65.0, true))
        }

        // P-SYNC-7, P-SYNC-AC5: the ebook's position and direction map to the banner state, and updates follow the flow
        it("#166 TC-62: device seconds and the ebook's seconds and source direction map, including behind (P-SYNC-7, P-SYNC-AC5)") {
            val fake = FakeResumeOffers()
            val vm = ResumeOffersViewModel(BOOK, fake)

            fake.offers.value = VisibleOffers(ebook = ebook.copy(positionSeconds = 65.0, sourceAhead = false))
            vm.uiState.value shouldBe ResumeOffersUIState(deviceSeconds = null, ebook = EbookOfferUIState(65.0, false))

            fake.offers.value = VisibleOffers(device = device)
            vm.uiState.value shouldBe ResumeOffersUIState(deviceSeconds = 300.0, ebook = null)

            fake.offers.value = VisibleOffers()
            vm.uiState.value shouldBe ResumeOffersUIState()
        }

        // R2, R3: Go and Dismiss on the device offer hand the offer back to the holder
        it("#166 TC-63: Go and Dismiss on the device offer forward it to the holder (R2, R3)") {
            val fake = FakeResumeOffers()
            val vm = ResumeOffersViewModel(BOOK, fake)
            fake.offers.value = VisibleOffers(device = device)

            vm.onGoDevice()
            vm.onDismissDevice()

            fake.calls shouldBe listOf(Call.AcceptDevice(device), Call.DismissDevice(device))
        }

        // R7, R8: Go and Dismiss on the ebook offer hand the offer back to the holder
        it("#166 TC-64: Go and Dismiss on the ebook offer forward it to the holder (R7, R8)") {
            val fake = FakeResumeOffers()
            val vm = ResumeOffersViewModel(BOOK, fake)
            fake.offers.value = VisibleOffers(ebook = ebook)

            vm.onGoEbook()
            vm.onDismissEbook()

            fake.calls shouldBe listOf(Call.AcceptEbook(ebook), Call.DismissEbook(ebook))
        }

        // R2, R7: an intent with no offer, or after the offer cleared, forwards nothing
        it("#166 TC-65: the intents forward nothing when no offer is showing (R2, R7)") {
            val fake = FakeResumeOffers()
            val vm = ResumeOffersViewModel(BOOK, fake)

            vm.onGoDevice()
            vm.onDismissDevice()
            vm.onGoEbook()
            vm.onDismissEbook()

            fake.offers.value = VisibleOffers(device = device, ebook = ebook)
            fake.offers.value = VisibleOffers()
            vm.onGoDevice()
            vm.onDismissDevice()
            vm.onGoEbook()
            vm.onDismissEbook()

            fake.calls shouldBe emptyList()
        }
    }
})
