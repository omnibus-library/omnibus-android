package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.quotecard.QuoteCardSheet
import com.omnibus.omnibus.feature.quotecard.passageShareIntent

/**
 * The Highlights tab's list. It renders nothing for a book with no uniqueIdentifier, before it creates its ViewModel, so
 * such a book needs no Hilt. Each row's actions open the quote card sheet, or share the passage as text.
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
        val androidContext = LocalContext.current
        var quotedPassage by rememberSaveable { mutableStateOf<String?>(null) }
        HighlightsContent(
            state,
            onRetry = viewModel::retry,
            modifier = modifier,
            rowActions = { row ->
                HighlightRowActions(
                    row,
                    onQuoteCard = { quotedPassage = it },
                    onShare = { androidContext.startActivity(passageShareIntent(it)) },
                )
            },
        )
        quotedPassage?.let {
            QuoteCardSheet(it, context.book.coverIdentity(), onDismiss = { quotedPassage = null })
        }
    }
}
