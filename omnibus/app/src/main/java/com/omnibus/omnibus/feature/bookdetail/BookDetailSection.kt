package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent

/**
 * A block of the Book Detail page, placed through a slot registry. It renders nothing when it does not apply to the
 * book, owns its own `@HiltViewModel(assistedFactory = …)` ViewModel (created with `hiltViewModel<VM, VM.Factory>`,
 * scoped to the nav entry), reloads on `LaunchedEffect(context.reloadKey)`, and reaches the page only through [context].
 */
fun interface BookDetailSection {
    @Composable
    fun Content(context: BookDetailSectionContext, modifier: Modifier)
}

/**
 * What the page gives every section. [reloadKey] changes on pull to refresh, Retry, [onReloadBook] and return to the
 * page from the reader or a pushed screen, and sections reload when it changes.
 */
@Immutable
data class BookDetailSectionContext(
    val book: Book,
    val accent: BookAccent,
    val reloadKey: Int,
    val tabs: BookDetailTabController,
    val onNavigate: (BookDetailDestination) -> Unit,
    val onShowMessage: (BookDetailMessage) -> Unit,
    val onBookChanged: (Book) -> Unit,
    val onReloadBook: () -> Unit,
    val onClose: () -> Unit,
)

/** A one-shot snackbar message. [holdUntilDismissed] keeps it up until the user dismisses it. */
data class BookDetailMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val holdUntilDismissed: Boolean = false,
)
