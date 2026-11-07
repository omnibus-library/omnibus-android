package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.feature.library.ui.LibraryScreenContent
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/** The Library tab's root entry in the harness. */
@Serializable
internal data object HarnessLibrary : NavKey

/** A Book Detail entry in the harness, for one book. */
@Serializable
internal data class HarnessDetail(val uuid: String) : NavKey

/**
 * A `SharedTransitionLayout` hosting a `NavDisplay` with the shell's fade specs, a Library entry that renders the real
 * [LibraryScreenContent] and a Book Detail entry that renders the real [BookDetailScreenContent]. Tests drive it
 * through [stack], [library] and [detailOverride].
 *
 * @param reducedMotion passed to [ProvideBookCoverTransition]. Tests pass false explicitly, because the test
 *   devices run with animator scale 0.
 * @param restorable keeps the transition state through [androidx.compose.ui.test.junit4.StateRestorationTester].
 */
internal class CoverTransitionHarness(
    books: List<Book>,
    cards: List<ContinueCard> = emptyList(),
    private val reducedMotion: Boolean = false,
    private val restorable: Boolean = false,
) {
    private val allBooks = books

    /** The navigation stack. Pushing a [HarnessDetail] is an open; popping it is a back. */
    val stack = mutableStateListOf<NavKey>(HarnessLibrary)

    /** The Library's state. Tests replace it to change what the grid and carousel show. */
    var library by mutableStateOf(
        LibraryUIState(
            books = books,
            hasLoaded = true,
            continueCards = cards,
            continueSelectedId = cards.firstOrNull()?.id,
        ),
    )

    /** The Book Detail state. Null means the book is loaded; `BookDetailUIState()` means it is still loading. */
    var detailOverride by mutableStateOf<BookDetailUIState?>(null)

    /** The transition state the harness hosts. Set on each composition. */
    lateinit var state: BookCoverTransitionState
        private set

    /** The shared-transition scope of the host. Set on each composition. */
    lateinit var sharedScope: SharedTransitionScope
        private set

    /** The uuids the Library opened, in order. */
    val opened = mutableListOf<String>()

    /** The card ids whose resume area was tapped, in order. */
    val resumed = mutableListOf<String>()

    /** The book with [uuid], or a generic book when there is none. */
    fun bookFor(uuid: String): Book = allBooks.firstOrNull { it.uuid == uuid } ?: testDetailBook()

    @Composable
    fun Content() {
        state = if (restorable) rememberBookCoverTransitionState() else remember { BookCoverTransitionState() }
        OmnibusTheme(darkTheme = true) {
            Column(Modifier.fillMaxSize()) {
                SharedTransitionLayout(Modifier.weight(1f)) {
                    sharedScope = this
                    ProvideBookCoverTransition(this, reducedMotion = reducedMotion, state = state) {
                        NavDisplay(
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeWindowInsets(WindowInsets.navigationBars),
                            backStack = stack,
                            onBack = { stack.removeLastOrNull() },
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            popTransitionSpec = { fadeIn() togetherWith fadeOut() },
                            predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
                            entryProvider = entryProvider {
                                entry<HarnessLibrary> {
                                    LibraryScreenContent(
                                        uiState = library,
                                        onRetry = {},
                                        onRefresh = {},
                                        onLoadMore = {},
                                        onOpenBook = { uuid ->
                                            opened += uuid
                                            stack.add(HarnessDetail(uuid))
                                        },
                                        onAddBooks = {},
                                        onTransientErrorShown = {},
                                        bottomInset = 0.dp,
                                        onResume = { resumed += it },
                                    )
                                }
                                entry<HarnessDetail> { key ->
                                    BookDetailScreenContent(
                                        uiState = detailOverride
                                            ?: BookDetailUIState(book = bookFor(key.uuid), isLoading = false),
                                        onNavigate = {},
                                        onClose = { stack.removeLastOrNull() },
                                        onRetry = {},
                                        onRefresh = {},
                                        onReloadBook = {},
                                        onBookChanged = {},
                                        onTransientErrorShown = {},
                                        slots = BookDetailSlots(),
                                        reducedMotion = true,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A Continue card for [book], as `LibraryScreenTest.continueCard` builds one. */
internal fun harnessCard(book: Book): ContinueCard = ContinueCard(
    id = "c${book.id}:epub",
    book = book,
    medium = ProgressFormat.Reading,
    fraction = 0.5,
    position = PositionLabel.PercentRead(50),
    lastOpened = RelativeAge.Hours(2),
    resumeFileId = null,
)

/**
 * The tag of a grid cell's cover under the host. It is the source tag, which replaces the cover tag on the same node.
 */
internal fun gridSourceTag(uuid: String): String = BookCoverTransitionTestTags.source(BookCoverKey(uuid, BookCoverOrigin.Grid))

/** The tag of a Continue card's cover under the host. It is the source tag, which replaces the cover tag. */
internal fun continueSourceTag(uuid: String): String =
    BookCoverTransitionTestTags.source(BookCoverKey(uuid, BookCoverOrigin.Continue))

/** The node's bounds in root coordinates, in pixels. Relative checks only, so no screen size is assumed. */
internal fun SemanticsNodeInteraction.rootRect(): Rect = fetchSemanticsNode().boundsInRoot

/** The moving stage cover of [uuid]. It is tagged inside the tilted box, so it moves with the transition. */
internal fun ComposeTestRule.movingStage(uuid: String): SemanticsNodeInteraction =
    onNodeWithTag(BookCoverTransitionTestTags.stage(uuid), useUnmergedTree = true)

/** The settled page cover, the untilted outer box the stage cover returns to. */
internal fun ComposeTestRule.stageCover(): SemanticsNodeInteraction =
    onNodeWithTag(BookDetailTestTags.COVER, useUnmergedTree = true)

/** True when [mid] lies strictly between [a] and [b]. */
internal fun strictlyBetween(a: Float, mid: Float, b: Float): Boolean = mid > minOf(a, b) && mid < maxOf(a, b)

/**
 * Asserts that [moving] lies between [from] and [to] on top and width, and differs from both. Relative only: the
 * positions come from the same run's own bounds.
 */
internal fun assertInTransit(moving: Rect, from: Rect, to: Rect, message: String) {
    assertTrue("$message: top ${moving.top} not between ${from.top} and ${to.top}", strictlyBetween(from.top, moving.top, to.top))
    assertTrue("$message: width ${moving.width} not between ${from.width} and ${to.width}", strictlyBetween(from.width, moving.width, to.width))
    assertNotEquals("$message: moving bounds equal the source", from, moving)
    assertNotEquals("$message: moving bounds equal the target", to, moving)
}

/** Asserts that two rects match to within [tolerance] pixels on every edge. */
internal fun assertSameRect(expected: Rect, actual: Rect, message: String, tolerance: Float = 1f) {
    assertEquals("$message: left", expected.left, actual.left, tolerance)
    assertEquals("$message: top", expected.top, actual.top, tolerance)
    assertEquals("$message: right", expected.right, actual.right, tolerance)
    assertEquals("$message: bottom", expected.bottom, actual.bottom, tolerance)
}

/** Taps [node] with the clock running and waits until the open has finished. */
internal fun ComposeTestRule.tapAndSettle(node: SemanticsNodeInteraction) {
    mainClock.autoAdvance = true
    node.performClick()
    waitForIdle()
}

/** Pops the top entry with the clock running and waits until the back has finished. */
internal fun ComposeTestRule.backAndSettle(h: CoverTransitionHarness) {
    mainClock.autoAdvance = true
    runOnIdle { h.stack.removeLastOrNull() }
    waitForIdle()
}

/**
 * Opens the book from [tap] and returns the settled stage cover's bounds, then goes back, so a later run can start
 * from the same place. This is the reference the mid-move checks compare against.
 */
internal fun ComposeTestRule.settledStageRect(h: CoverTransitionHarness, tap: SemanticsNodeInteraction): Rect {
    tapAndSettle(tap)
    val settled = stageCover().rootRect()
    backAndSettle(h)
    return settled
}
