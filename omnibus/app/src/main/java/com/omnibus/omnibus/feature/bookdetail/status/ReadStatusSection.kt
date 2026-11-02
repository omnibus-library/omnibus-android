package com.omnibus.omnibus.feature.bookdetail.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * The read status pill at the start of the stage's status row. It renders nothing for a book with nothing to track,
 * before it creates its ViewModel, so such a book needs no Hilt.
 */
internal object ReadStatusSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsReadStatus(context.book)) return
        val viewModel = hiltViewModel<ReadStatusViewModel, ReadStatusViewModel.Factory>(
            key = "read-status-${context.book.uuid}",
            creationCallback = { it.create(context.book.uuid) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        ReadStatusSaveMessage(state.saveFailure, context.onShowMessage, viewModel::saveFailureShown)
        val rim = rememberStatusRim(context.book)
        ReadStatusPill(state, context.accent, rim, onSelect = viewModel::selectStatus, modifier = modifier)
    }
}

/**
 * Whether the book has something to track: an ebook, an audiobook, a physical copy, or a wishlist entry. Matches
 * iOS `showsReadStatus`.
 */
internal fun showsReadStatus(book: Book): Boolean =
    book.hasEbook || book.hasAudiobook || book.hasPhysical || book.isWishlisted
