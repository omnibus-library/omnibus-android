package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/**
 * The Series section of the Overview tab: the series as a fan of covers (issue #104).
 *
 * It renders nothing when the book has no series id or a blank series name, and nothing while the members are not
 * known or when the series holds fewer than two books. The gate runs before any ViewModel is touched, so a book
 * without a series never reaches Hilt. The spread state is kept per book, and it survives recreation.
 */
internal object SeriesFanSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val book = context.book
        val seriesId = book.seriesId ?: return
        if (book.series.isNullOrBlank()) return
        val members = LocalSeriesSource.current.members(context, seriesId)
        val model = remember(book, members) { seriesFanModel(book, members) } ?: return
        var spread by rememberSaveable(book.uuid) { mutableStateOf(false) }
        SeriesFan(
            model = model,
            spread = spread,
            onToggleSpread = { spread = !spread },
            onOpenBook = { context.onNavigate(BookDetailDestination.Book(it)) },
            onOpenSeriesPage = { context.onNavigate(BookDetailDestination.Series(model.seriesId, model.seriesName)) },
            ribbonColor = context.accent.tint,
            reducedMotion = rememberReducedMotion(),
            modifier = modifier,
        )
    }
}
