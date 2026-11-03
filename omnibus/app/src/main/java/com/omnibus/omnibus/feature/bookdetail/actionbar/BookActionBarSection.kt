package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * The bottom action bar of the Book Detail page. It is self-gating: a book with no readable copy renders the untappable
 * [ActionBarModel.NoCopy] before any ViewModel is created, so such a book needs no Hilt.
 */
internal object BookActionBarSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val book = context.book
        val colors = remember(book) { actionBarColors(book) }
        if (!hasReadableCopy(book)) {
            BookActionBar(ActionBarModel.NoCopy(physical = book.hasPhysical), colors, context.onNavigate, modifier)
            return
        }
        val viewModel = hiltViewModel<ActionBarViewModel, ActionBarViewModel.Factory>(
            key = "action-bar-${book.uuid}",
            creationCallback = { it.create(book.uuid) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        val model = remember(book, state) { actionBarModel(book, state.status, state.progress) }
        BookActionBar(model, colors, context.onNavigate, modifier)
    }
}
