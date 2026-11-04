package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.offers.EbookOffer
import com.omnibus.omnibus.data.audiobook.offers.ResumeOffers
import com.omnibus.omnibus.data.audiobook.position.LatePosition
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the banner slot shows for the offers: the device offer's position, and the ebook offer's. Plain values only.
 *
 * @property deviceSeconds The device offer's position in book time, or null when there is no device offer.
 * @property ebook The ebook offer, or null when there is none.
 */
data class ResumeOffersUIState(
    val deviceSeconds: Double? = null,
    val ebook: EbookOfferUIState? = null,
)

/**
 * The ebook offer as the banner shows it.
 *
 * @property seconds The audio position the ebook reading maps to, in book time.
 * @property sourceAhead False when the ebook is behind the audio (a re-read), so the banner says so.
 */
data class EbookOfferUIState(
    val seconds: Double,
    val sourceAhead: Boolean,
)

/**
 * The resume offers of one player (#166). It shows only the offers of [bookUuid], so a player for another book never
 * shows this book's offer. Go and Dismiss forward the offer it last saw to [ResumeOffers]; with no offer they do nothing.
 *
 * The offers' memory is per load and lives in [ResumeOffers], not here, so the ViewModel survives a reload as it is.
 */
@HiltViewModel(assistedFactory = ResumeOffersViewModel.Factory::class)
class ResumeOffersViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val offers: ResumeOffers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResumeOffersUIState())

    /** The offers to show now. */
    val uiState: StateFlow<ResumeOffersUIState> = _uiState.asStateFlow()

    /** The domain offers behind [uiState], kept so the intents can hand them back to [ResumeOffers]. */
    private var device: LatePosition? = null
    private var ebook: EbookOffer? = null

    init {
        viewModelScope.launch {
            offers.getOffersFlow().collect { visible ->
                device = visible.device?.takeIf { it.bookUuid == bookUuid }
                ebook = visible.ebook?.takeIf { it.bookUuid == bookUuid }
                _uiState.update {
                    ResumeOffersUIState(
                        deviceSeconds = device?.positionSeconds,
                        ebook = ebook?.let { EbookOfferUIState(it.positionSeconds, it.sourceAhead) },
                    )
                }
            }
        }
    }

    /** Go on the device offer. */
    fun onGoDevice() {
        device?.let(offers::acceptDeviceOffer)
    }

    /** Dismiss on the device offer. */
    fun onDismissDevice() {
        device?.let(offers::dismissDeviceOffer)
    }

    /** Go on the ebook offer. */
    fun onGoEbook() {
        ebook?.let(offers::acceptEbookOffer)
    }

    /** Dismiss on the ebook offer. */
    fun onDismissEbook() {
        ebook?.let(offers::dismissEbookOffer)
    }

    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ResumeOffersViewModel
    }
}
