package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.runtime.Composable
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** A Book Detail shot's book: no cover, an epub, one linked author unless overridden. */
internal fun shotBook(
    title: String? = "The Left Hand of Darkness",
    creators: List<Creator> = listOf(Creator("Ursula K. Le Guin", "author", null, 7L)),
    series: String? = null,
    seriesIndex: String? = null,
    publishedDate: String? = null,
    description: String? = null,
    genres: List<String> = emptyList(),
    subjects: List<String> = emptyList(),
): Book = Book(
    id = 1,
    uniqueIdentifier = "uuid-1",
    filename = "book-1.epub",
    title = title,
    creators = creators,
    coverUrl = null,
    accent = null,
    formats = listOf("epub"),
    series = series,
    seriesIndex = seriesIndex,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
    description = description,
    genres = genres,
    subjects = subjects,
    publishedDate = publishedDate,
)

/** A context for rendering one section alone, with an accent for the current theme and callbacks that do nothing. */
@Composable
internal fun shotSectionContext(book: Book = shotBook()): BookDetailSectionContext = BookDetailSectionContext(
    book = book,
    accent = bookAccent(book.coverIdentity().tone, isLightGround(OmnibusTheme.palette.bg0)),
    reloadKey = 0,
    tabs = InertBookDetailTabController,
    onNavigate = {},
    onShowMessage = {},
    onBookChanged = {},
    onReloadBook = {},
    onClose = {},
)
