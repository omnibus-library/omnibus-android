package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.position.LatePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A [ResumeOffers] whose visible offers tests set, recording every command in call order. */
class FakeResumeOffers : ResumeOffers {

    /** Each command the holder was given, in call order. */
    sealed interface Call {
        data class AcceptDevice(val offer: LatePosition) : Call

        data class DismissDevice(val offer: LatePosition) : Call

        data class AcceptEbook(val offer: EbookOffer) : Call

        data class DismissEbook(val offer: EbookOffer) : Call
    }

    /** The offers a test sets. They show as the holder's visible offers. */
    val offers = MutableStateFlow(VisibleOffers())

    val calls = mutableListOf<Call>()

    override fun getOffersFlow(): Flow<VisibleOffers> = offers

    override fun acceptDeviceOffer(offer: LatePosition) {
        calls += Call.AcceptDevice(offer)
    }

    override fun dismissDeviceOffer(offer: LatePosition) {
        calls += Call.DismissDevice(offer)
    }

    override fun acceptEbookOffer(offer: EbookOffer) {
        calls += Call.AcceptEbook(offer)
    }

    override fun dismissEbookOffer(offer: EbookOffer) {
        calls += Call.DismissEbook(offer)
    }
}
