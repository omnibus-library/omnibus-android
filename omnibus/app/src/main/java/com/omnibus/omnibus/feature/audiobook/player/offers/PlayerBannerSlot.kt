package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat
import com.omnibus.omnibus.feature.audiobook.player.PlayerErrorBanner

/**
 * The player's banner slot above the cover (#166): the playback error, or else the device offer, or else the ebook
 * offer, or nothing (P-SYNC-8). The offers come from [ResumeOffersViewModel], keyed by [bookUuid] so each player keeps
 * its own.
 *
 * @param bookUuid The book the player shows.
 * @param error The playback error, or null. It always wins the slot.
 * @param onRetry Called when the listener taps Retry on the error.
 */
@Composable
fun PlayerBannerSlot(
    bookUuid: String,
    error: PlaybackError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<ResumeOffersViewModel, ResumeOffersViewModel.Factory>(
        key = "resume-offers-$bookUuid",
        creationCallback = { it.create(bookUuid) },
    )
    val offers by viewModel.uiState.collectAsState()
    PlayerBannerSlotContent(
        banner = playerBanner(error, offers),
        onRetry = onRetry,
        onGoDevice = viewModel::onGoDevice,
        onDismissDevice = viewModel::onDismissDevice,
        onGoEbook = viewModel::onGoEbook,
        onDismissEbook = viewModel::onDismissEbook,
        modifier = modifier,
    )
}

/**
 * The stateless part of [PlayerBannerSlot]: draws [banner], or nothing for null, so the slot collapses as it did before.
 */
@Composable
internal fun PlayerBannerSlotContent(
    banner: PlayerBanner?,
    onRetry: () -> Unit,
    onGoDevice: () -> Unit,
    onDismissDevice: () -> Unit,
    onGoEbook: () -> Unit,
    onDismissEbook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (banner) {
        null -> Unit
        is PlayerBanner.Error -> PlayerErrorBanner(error = banner.error, onRetry = onRetry, modifier = modifier)
        is PlayerBanner.DeviceOffer -> ResumeOfferBanner(
            title = stringResource(R.string.audiobook_offers_device_title),
            detail = stringResource(
                R.string.audiobook_offers_device_detail,
                PlaybackTimeFormat.clock(banner.seconds),
            ),
            onGo = onGoDevice,
            onDismiss = onDismissDevice,
            modifier = modifier,
        )
        is PlayerBanner.EbookOffer -> ResumeOfferBanner(
            title = stringResource(
                if (banner.sourceAhead) {
                    R.string.audiobook_offers_ebook_ahead_title
                } else {
                    R.string.audiobook_offers_ebook_behind_title
                },
            ),
            detail = stringResource(
                R.string.audiobook_offers_ebook_detail,
                PlaybackTimeFormat.clock(banner.seconds),
            ),
            onGo = onGoEbook,
            onDismiss = onDismissEbook,
            modifier = modifier,
        )
    }
}
