package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
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
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Oklch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
        val physical = mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                // A book with no readable copy: the gate renders the note before any ViewModel, so no Hilt is needed.
                val book = testDetailBook(formats = emptyList()).copy(hasPhysical = physical.value)
                BookActionBarSection.Content(testSectionContext(book = book), Modifier.fillMaxWidth())
            }
        }

        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_physical_copy))

        rule.runOnIdle { physical.value = false }
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY)
            .assertTextEquals(detailText(R.string.book_detail_actionbar_no_copy))
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
