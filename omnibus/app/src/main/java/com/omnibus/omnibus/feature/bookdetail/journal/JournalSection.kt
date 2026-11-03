package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * The Journal list under the Highlights. It is keyed by the book's uniqueIdentifier, which the server uses for the journal.
 * It renders nothing for a book without one, before it creates its ViewModel, so such a book needs no Hilt.
 */
internal object JournalSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val uuid = context.book.uniqueIdentifier
        if (uuid.isNullOrBlank()) return
        val viewModel = hiltViewModel<JournalViewModel, JournalViewModel.Factory>(
            key = "journal-$uuid",
            creationCallback = { it.create(uuid) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        JournalContent(state, onRetry = viewModel::retry, modifier = modifier)
    }
}
