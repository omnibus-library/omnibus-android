package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * Where the stage line and the Series section get their members. Tests provide their own source
 * through [LocalSeriesSource] so they never reach Hilt.
 */
internal fun interface SeriesSource {

    /** The members of [seriesId], or null while they are not known. */
    @Composable
    fun members(context: BookDetailSectionContext, seriesId: Long): List<SeriesMember>?
}

/** The production source: a [SeriesViewModel] keyed by series id, so both callers in one nav entry share it. */
internal object HiltSeriesSource : SeriesSource {

    @Composable
    override fun members(context: BookDetailSectionContext, seriesId: Long): List<SeriesMember>? {
        val viewModel = hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            key = "series-$seriesId",
            creationCallback = { it.create(seriesId) },
        )
        LaunchedEffect(viewModel, context.reloadKey) { viewModel.reload(context.reloadKey) }
        return viewModel.uiState.collectAsState().value.members
    }
}

/** The active [SeriesSource]. Production keeps [HiltSeriesSource]. */
internal val LocalSeriesSource = staticCompositionLocalOf<SeriesSource> { HiltSeriesSource }
