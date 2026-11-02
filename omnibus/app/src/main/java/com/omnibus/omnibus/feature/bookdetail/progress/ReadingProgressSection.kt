package com.omnibus.omnibus.feature.bookdetail.progress

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
 * The reading progress under the stage's status row. It renders nothing for a book with no file, before it creates its
 * ViewModel, so such a book needs no Hilt.
 */
internal object ReadingProgressSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!hasProgressFile(context.book)) return
        val viewModel = hiltViewModel<ReadingProgressViewModel, ReadingProgressViewModel.Factory>(
            key = "reading-progress-${context.book.uuid}",
            creationCallback = { it.create(context.book.uuid, context.book.hasEbook) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        ReadingProgressContent(state, context.accent, modifier)
    }
}

/** Whether the book has a file the reader can measure progress in: an ebook or an audiobook. */
internal fun hasProgressFile(book: Book): Boolean = book.hasEbook || book.hasAudiobook
