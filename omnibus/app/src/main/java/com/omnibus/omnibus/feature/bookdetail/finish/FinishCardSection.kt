package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.overview.series.LocalSeriesSource
import com.omnibus.omnibus.feature.bookdetail.reviews.sheet.rememberReviewSheetLauncher
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/** The book's one finish ViewModel, shared by the card and the cover overlay through the key in one nav entry. */
@Composable
internal fun finishCardViewModel(bookUuid: String): FinishCardViewModel =
    hiltViewModel<FinishCardViewModel, FinishCardViewModel.Factory>(
        key = "finish-card-$bookUuid",
        creationCallback = { it.create(bookUuid) },
    )

/**
 * The finish card under the stage's status row (#97). It renders nothing, and creates no ViewModel, for a book with no
 * ebook or audiobook; then nothing until the status is Finished.
 */
internal object FinishCardSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val book = context.book
        if (!showsFinishCard(book)) return
        val viewModel = finishCardViewModel(book.uuid)
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        val celebration by viewModel.celebration.collectAsState()
        if (!state.finished) return
        val seriesId = book.seriesId
        val members = if (state.wantsSeries && seriesId != null && !book.series.isNullOrBlank()) {
            LocalSeriesSource.current.members(context, seriesId)
        } else {
            null
        }
        LaunchedEffect(members, context.reloadKey) {
            if (members != null) viewModel.loadSeriesStatuses(members, context.reloadKey)
        }
        val openReview = rememberReviewSheetLauncher(book.uuid)
        val reducedMotion = rememberReducedMotion()
        val model = remember(book, state, members) { finishCardModel(book, state, members) }
        val onNavigate = context.onNavigate
        RisingFinishCard(
            model = model,
            ground = rememberFinishGround(book),
            rises = celebration.rises,
            latch = viewModel.riseLatch,
            reducedMotion = reducedMotion,
            onLeaveReview = openReview,
            onOpenBook = { uuid -> onNavigate(BookDetailDestination.Book(uuid)) },
            modifier = modifier,
        )
    }
}

/** The burst over the stage cover (#97), with the cover's replay tap. Same gate as [FinishCardSection]. */
internal object FinishCelebrationSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsFinishCard(context.book)) return
        val viewModel = finishCardViewModel(context.book.uuid)
        val state by viewModel.uiState.collectAsState()
        val celebration by viewModel.celebration.collectAsState()
        FinishBurst(
            finished = state.finished,
            bursts = celebration.bursts,
            reducedMotion = rememberReducedMotion(),
            accent = context.accent.tint,
            latch = viewModel.burstLatch,
            onReplay = viewModel::replayBurst,
            modifier = modifier,
        )
    }
}
