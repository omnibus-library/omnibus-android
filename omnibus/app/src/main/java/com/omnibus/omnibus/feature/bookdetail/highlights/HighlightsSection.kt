package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * The Highlights tab's list. It renders nothing for a book with no uniqueIdentifier, before it creates its ViewModel, so
 * such a book needs no Hilt.
 */
internal object HighlightsSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (context.book.uniqueIdentifier.isNullOrBlank()) return
        val viewModel = hiltViewModel<HighlightsViewModel, HighlightsViewModel.Factory>(
            key = "highlights-${context.book.uuid}",
            creationCallback = { it.create(context.book.uuid) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        HighlightsContent(state, onRetry = viewModel::retry, modifier = modifier)
    }
}
