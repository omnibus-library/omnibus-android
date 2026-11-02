package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.coroutines.runBlocking

/** A Book Detail test book: no cover (nothing loads), an epub, one linked author unless overridden. */
fun testDetailBook(
    id: Long = 1,
    title: String? = "The Left Hand of Darkness",
    creators: List<Creator> = listOf(Creator("Ursula K. Le Guin", "author", null, 7L)),
    formats: List<String> = listOf("epub"),
    uniqueIdentifier: String? = "uuid-$id",
    series: String? = null,
    seriesIndex: String? = null,
    publishedDate: String? = null,
    accent: String? = null,
): Book = Book(
    id = id,
    uniqueIdentifier = uniqueIdentifier,
    filename = "book-$id.epub",
    title = title,
    creators = creators,
    coverUrl = null,
    accent = accent,
    formats = formats,
    series = series,
    seriesIndex = seriesIndex,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
    publishedDate = publishedDate,
)

/** What a test context's callbacks received, in order. */
class SectionContextRecorder {
    val navigations = mutableListOf<BookDetailDestination>()
    val messages = mutableListOf<BookDetailMessage>()
    val changedBooks = mutableListOf<Book>()
    var reloadCount = 0
    var closeCount = 0
}

/** A context for rendering one section alone; its callbacks record into [recorder]. */
fun testSectionContext(
    book: Book = testDetailBook(),
    reloadKey: Int = 0,
    accent: BookAccent = bookAccent(book.coverIdentity().tone, lightGround = false),
    tabs: BookDetailTabController = InertBookDetailTabController,
    recorder: SectionContextRecorder = SectionContextRecorder(),
): BookDetailSectionContext = BookDetailSectionContext(
    book = book,
    accent = accent,
    reloadKey = reloadKey,
    tabs = tabs,
    onNavigate = { recorder.navigations += it },
    onShowMessage = { recorder.messages += it },
    onBookChanged = { recorder.changedBooks += it },
    onReloadBook = { recorder.reloadCount++ },
    onClose = { recorder.closeCount++ },
)

/** The app's string [id], formatted with [args]. */
fun detailText(id: Int, vararg args: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

/** The node's bounds in root coordinates, in pixels. */
fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

/** The text layout of a `Text` node. Only a `Text` exposes [SemanticsActions.GetTextLayoutResult]. */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val out = mutableListOf<TextLayoutResult>()
    fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
    return out.first()
}

/**
 * Sets [BookDetailScreenContent] in dark theme with empty slots and recording callbacks. Use [setContent] directly
 * when a test changes its state.
 */
fun ComposeContentTestRule.setDetailScreen(
    uiState: BookDetailUIState,
    slots: BookDetailSlots = BookDetailSlots(),
    scrollState: ScrollState = ScrollState(0),
    onNavigate: (BookDetailDestination) -> Unit = {},
    onClose: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onReloadBook: () -> Unit = {},
    onBookChanged: (Book) -> Unit = {},
    onTransientErrorShown: () -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            BookDetailScreenContent(
                uiState = uiState,
                onNavigate = onNavigate,
                onClose = onClose,
                onRetry = onRetry,
                onRefresh = onRefresh,
                onReloadBook = onReloadBook,
                onBookChanged = onBookChanged,
                onTransientErrorShown = onTransientErrorShown,
                modifier = Modifier.fillMaxSize(),
                slots = slots,
                scrollState = scrollState,
            )
        }
    }
}

/** Scrolls [state] to [value] (its end by default) from the test thread, then waits for idle. */
fun ComposeContentTestRule.scrollTo(state: ScrollState, value: Int = state.maxValue) {
    runOnIdle { runBlocking { state.scrollTo(value) } }
    waitForIdle()
}
