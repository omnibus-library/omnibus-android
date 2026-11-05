package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Oklch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A fixed reading bar, so the screen tests do not need Hilt. */
private val TestBarSection = BookDetailSection { ctx, modifier ->
    BookActionBar(
        model = ActionBarModel.Primary(ActionLabel.ContinueReading(4), 0.4f, BookDetailDestination.Reader(ctx.book.uuid)),
        colors = actionBarColors(ctx.book),
        onNavigate = ctx.onNavigate,
        modifier = modifier,
        reducedMotion = true,
    )
}

/** A reading primary button for the book with [uuid], continuing at chapter 4. */
private fun readingPrimary(uuid: String) = ActionBarModel.Primary(ActionLabel.ContinueReading(4), 0.4f, BookDetailDestination.Reader(uuid))

/** The test book's audio files: two selectable parts, the first saved as the listening position. */
private val twoParts = listOf(
    BookFile(7L, "m4b", "part-one.m4b", ordinal = 1, label = null, sizeBytes = 412_000_000L, etag = null),
    BookFile(8L, "mp3", "part-two.mp3", ordinal = 2, label = null, sizeBytes = 0L, etag = null),
)

/** One selectable audio file, the saved position. */
private val onePart = listOf(BookFile(7L, "m4b", "whole.m4b", ordinal = 1, label = null, sizeBytes = 0L, etag = null))

private val chooserAccent = Oklch(0.62, 0.12, 40.0).toColor()

/**
 * Renders [BookActionBarContent] for [book] with [model] and [actions], in dark theme. The model is read from [model] so a
 * test can swap it in one composition. A tap's navigation and audio load are recorded in the callbacks the test passes.
 */
private fun ComposeContentTestRule.setBarContent(
    book: Book,
    model: State<ActionBarModel>,
    actions: List<RoundAction>,
    onNavigate: (BookDetailDestination) -> Unit = {},
    onLoadAudio: (Long?) -> Unit = {},
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            BookActionBarContent(
                bookUuid = book.uuid,
                model = model.value,
                roundActions = actions,
                colors = actionBarColors(book),
                listenColors = listenButtonColors(Oklch(0.62, 0.12, 40.0), lightGround = false),
                chooserAccent = chooserAccent,
                onNavigate = onNavigate,
                onLoadAudio = onLoadAudio,
                modifier = Modifier.fillMaxWidth(),
                reducedMotion = true,
            )
        }
    }
}

class BookActionBarSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-56
    @Test
    fun issue98_tc56_sectionShowsNoCopyWithoutHilt() {
        val recorder = SectionContextRecorder()
        val physical = mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                // A book with no readable copy: the gate renders the note before any ViewModel, so no Hilt is needed.
                val book = testDetailBook(formats = emptyList()).copy(hasPhysical = physical.value)
                BookActionBarSection.Content(testSectionContext(book = book, recorder = recorder), Modifier.fillMaxWidth())
            }
        }

        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_physical_copy))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY).performClick()
        rule.waitForIdle()
        assertTrue(recorder.navigations.isEmpty())

        rule.runOnIdle { physical.value = false }
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_no_copy))
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertCountEquals(0)
    }

    // TC-59
    @Test
    fun issue98_tc59_readPillIsAbsentWhenTheSlotIsFilled() {
        val book = mutableStateOf(testDetailBook(formats = listOf("epub")))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = book.value, isLoading = false),
                    onNavigate = {},
                    onClose = {},
                    onRetry = {},
                    onRefresh = {},
                    onReloadBook = {},
                    onBookChanged = {},
                    onTransientErrorShown = {},
                    modifier = Modifier.fillMaxSize(),
                    slots = BookDetailSlots(actionBar = listOf(TestBarSection)),
                )
            }
        }
        rule.onAllNodesWithTag(BookDetailTestTags.READ_PILL).assertCountEquals(0)
        rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).assertIsDisplayed()

        rule.runOnIdle { book.value = testDetailBook(formats = emptyList()) }
        rule.onAllNodesWithTag(BookDetailTestTags.READ_PILL).assertCountEquals(0)
        rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).assertIsDisplayed()
    }

    // TC-60
    @Test
    fun issue98_tc60_readPillIsPresentWhenTheSlotIsEmpty() {
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(formats = listOf("epub")), isLoading = false), slots = BookDetailSlots())
        rule.onNodeWithTag(BookDetailTestTags.READ_PILL).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.BUTTON).assertCountEquals(0)
    }

    // TC-4
    @Test
    fun issue99_tc4_bothRoundButtonsShow() {
        val book = testDetailBook(formats = listOf("epub", "m4b"), bookFiles = onePart)
        val model = readingPrimary(book.uuid)
        val actions = roundActions(book, model, savedAudioFileId = 7L, listenAvailable = true)
        rule.setBarContent(book, mutableStateOf(model), actions)

        rule.onNodeWithTag(ActionBarTestTags.BUTTON).assertIsDisplayed()
        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).assertIsDisplayed()
        rule.onNodeWithTag(ActionBarTestTags.ROUND_IMMERSIVE).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_READ).assertCountEquals(0)
    }

    // TC-5
    @Test
    fun issue99_tc5_noCopyAndWishlistShowNoRoundButtons() {
        val book = testDetailBook(formats = emptyList())
        val wishlist = ActionBarModel.Wishlist(
            findCopy = BookDetailDestination.ExternalUrl("https://example.invalid/search"),
            checkIn = BookDetailDestination.CheckIn(book.uuid),
        )
        val model = mutableStateOf<ActionBarModel>(ActionBarModel.NoCopy(physical = false))
        rule.setBarContent(book, model, actions = emptyList())
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_READ).assertCountEquals(0)
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_LISTEN).assertCountEquals(0)
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_IMMERSIVE).assertCountEquals(0)

        rule.runOnIdle { model.value = wishlist }
        rule.onNodeWithTag(ActionBarTestTags.CHECK_IN).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_READ).assertCountEquals(0)
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_LISTEN).assertCountEquals(0)
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_IMMERSIVE).assertCountEquals(0)
    }

    // TC-6
    @Test
    fun issue99_tc6_singleFormatShowsNoRoundButtons() {
        val book = testDetailBook(formats = listOf("epub"))
        rule.setBarContent(book, mutableStateOf(readingPrimary(book.uuid)), actions = emptyList())

        rule.onNodeWithTag(ActionBarTestTags.BUTTON).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_READ).assertCountEquals(0)
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_LISTEN).assertCountEquals(0)
        rule.onAllNodesWithTag(ActionBarTestTags.ROUND_IMMERSIVE).assertCountEquals(0)
    }

    // TC-27
    @Test
    fun issue99_tc27_listenOneFileNavigatesToPlayer() {
        val book = testDetailBook(formats = listOf("epub", "m4b"), bookFiles = onePart)
        val navigations = mutableListOf<BookDetailDestination>()
        val model = readingPrimary(book.uuid)
        val actions = roundActions(book, model, savedAudioFileId = 7L, listenAvailable = true)
        rule.setBarContent(book, mutableStateOf(model), actions, onNavigate = { navigations += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).performClick()
        rule.waitForIdle()

        assertEquals(listOf(BookDetailDestination.Player(book.uuid, 7L)), navigations)
        rule.onAllNodesWithTag(AudioFileChooserTestTags.SHEET).assertCountEquals(0)
    }

    // TC-34
    @Test
    fun issue99_tc34_listenMultiFileOpensChooserNotPlayer() {
        val book = testDetailBook(formats = listOf("epub", "m4b"), bookFiles = twoParts)
        val navigations = mutableListOf<BookDetailDestination>()
        val model = readingPrimary(book.uuid)
        val actions = roundActions(book, model, savedAudioFileId = 7L, listenAvailable = true)
        rule.setBarContent(book, mutableStateOf(model), actions, onNavigate = { navigations += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).performClick()
        rule.waitForIdle()

        rule.onAllNodesWithTag(AudioFileChooserTestTags.SHEET).assertCountEquals(1)
        assertEquals(emptyList<BookDetailDestination>(), navigations)
    }

    // TC-39
    @Test
    fun issue99_tc39_pickingFileOpensPlayerWithThatFile() {
        val book = testDetailBook(formats = listOf("epub", "m4b"), bookFiles = twoParts)
        val navigations = mutableListOf<BookDetailDestination>()
        val model = readingPrimary(book.uuid)
        val actions = roundActions(book, model, savedAudioFileId = 7L, listenAvailable = true)
        rule.setBarContent(book, mutableStateOf(model), actions, onNavigate = { navigations += it })

        rule.onNodeWithTag(ActionBarTestTags.ROUND_LISTEN).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(AudioFileChooserTestTags.row(8L)).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { navigations.isNotEmpty() }
        rule.waitForIdle()

        assertEquals(listOf(BookDetailDestination.Player(book.uuid, 8L)), navigations)
        rule.onAllNodesWithTag(AudioFileChooserTestTags.SHEET).assertCountEquals(0)
    }

    // TC-49
    @Test
    fun issue99_tc49_immersiveLoadsThenOpensReader() {
        val book = testDetailBook(formats = listOf("epub", "m4b"), bookFiles = onePart)
        val events = mutableListOf<String>()
        val model = readingPrimary(book.uuid)
        val reader = BookDetailDestination.Reader(book.uuid)
        rule.setBarContent(
            book,
            mutableStateOf(model),
            listOf(RoundAction.ImmersiveRead(reader, audioFileId = 7L, loadsAudio = true)),
            onNavigate = { events += "navigate:$it" },
            onLoadAudio = { events += "load:$it" },
        )

        rule.onNodeWithTag(ActionBarTestTags.ROUND_IMMERSIVE).performClick()
        rule.waitForIdle()

        assertEquals(listOf("load:7", "navigate:$reader"), events)
        rule.onAllNodesWithTag(AudioFileChooserTestTags.SHEET).assertCountEquals(0)
    }

    // TC-50
    @Test
    fun issue99_tc50_immersiveOfflineOpensReaderWithoutLoad() {
        val book = testDetailBook(formats = listOf("epub", "m4b"), bookFiles = onePart)
        val events = mutableListOf<String>()
        val model = readingPrimary(book.uuid)
        val reader = BookDetailDestination.Reader(book.uuid)
        rule.setBarContent(
            book,
            mutableStateOf(model),
            listOf(RoundAction.ImmersiveRead(reader, audioFileId = 7L, loadsAudio = false)),
            onNavigate = { events += "navigate:$it" },
            onLoadAudio = { events += "load:$it" },
        )

        rule.onNodeWithTag(ActionBarTestTags.ROUND_IMMERSIVE).performClick()
        rule.waitForIdle()

        assertEquals(listOf("navigate:$reader"), events)
    }
}
