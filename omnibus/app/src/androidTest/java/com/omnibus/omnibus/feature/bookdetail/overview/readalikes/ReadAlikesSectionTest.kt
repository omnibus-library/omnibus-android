package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.suggestions.FakeSuggestionsRepository
import com.omnibus.omnibus.data.suggestions.SuggestionCoverResult
import com.omnibus.omnibus.data.suggestions.SuggestionsAnswer
import com.omnibus.omnibus.data.suggestions.SuggestionsError
import com.omnibus.omnibus.data.suggestions.SuggestionsResult
import com.omnibus.omnibus.data.suggestions.pending
import com.omnibus.omnibus.data.suggestions.ready
import com.omnibus.omnibus.data.suggestions.suggestion
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.overview.shelf.BookShelfTestTags
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

class ReadAlikesSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = testDetailBook()

    private val unavailableAnswer: SuggestionsResult = SuggestionsResult.Success(SuggestionsAnswer.Unavailable)
    private val notConfiguredAnswer: SuggestionsResult = SuggestionsResult.Success(SuggestionsAnswer.NotConfigured)
    private val failure: SuggestionsResult = SuggestionsResult.Failure(SuggestionsError.Unreachable)

    private fun viewModelFor(fake: FakeSuggestionsRepository) = ReadAlikesViewModel("uuid-1", fake)

    /** Renders the section for [viewModel] at a fixed reload key. */
    private fun setSection(viewModel: ReadAlikesViewModel, recorder: SectionContextRecorder = SectionContextRecorder()) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadAlikesContent(testSectionContext(book = book, recorder = recorder), viewModel)
            }
        }
    }

    /** How many suggestion cards are on screen. */
    private fun cardCount(): Int = rule.onAllNodesWithTag(BookShelfTestTags.CARD).fetchSemanticsNodes().size

    /** The text of the status line, or null when it is not on screen. */
    private fun statusText(): String? =
        rule.onAllNodesWithTag(ReadAlikesTestTags.STATUS_TEXT, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text

    /** The text of each card, in order: its merged title and author. */
    private fun cardTexts(): List<List<String>> =
        rule.onAllNodesWithTag(BookShelfTestTags.CARD).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }

    /** The text of the node with [tag], one entry per Text in its merged config. */
    private fun textsOf(tag: String): List<String> =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }

    /** A small valid PNG, made at run time so the test needs no resource file. */
    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    // #106 TC-74 (AC2)
    @Test
    fun tc74_pendingShowsTheSpinnerLineThenTheShelfReplacesIt() {
        val fake = FakeSuggestionsRepository(pending, ready(suggestion(1), suggestion(2)))
        setSection(viewModelFor(fake))

        rule.waitUntil(5_000) { statusText() == detailText(R.string.book_detail_readalikes_searching) }
        rule.onAllNodesWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertCountEquals(1)
        assertEquals(0, cardCount())

        rule.mainClock.advanceTimeBy(4_000)
        rule.waitUntil(10_000) { fake.requests >= 2 }
        rule.waitUntil(5_000) { cardCount() == 2 }
        rule.onAllNodesWithTag(ReadAlikesTestTags.STATUS).assertCountEquals(0)
        rule.onAllNodesWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertCountEquals(0)
    }

    // #106 TC-75 (AC1)
    @Test
    fun tc75_aReadyAnswerShowsTheShelfNamingTheBook() {
        val fake = FakeSuggestionsRepository(ready(suggestion(1), suggestion(2), suggestion(3)))
        setSection(viewModelFor(fake))

        rule.waitUntil(5_000) { cardCount() == 3 }
        assertEquals(
            listOf(detailText(R.string.book_detail_readalikes_heading, book.displayTitle)),
            textsOf(ReadAlikesTestTags.HEADING),
        )
    }

    // #106 TC-76 (R9)
    @Test
    fun tc76_tappingACardNavigatesToItsHardcoverUrl() {
        val recorder = SectionContextRecorder()
        val second = suggestion(2)
        val fake = FakeSuggestionsRepository(ready(suggestion(1), second))
        setSection(viewModelFor(fake), recorder = recorder)

        rule.waitUntil(5_000) { cardCount() == 2 }
        rule.onAllNodesWithTag(BookShelfTestTags.CARD)[1].performClick()
        rule.waitForIdle()

        assertEquals(listOf<BookDetailDestination>(BookDetailDestination.ExternalUrl(second.hardcoverUrl)), recorder.navigations)
    }

    // #106 TC-77 (AC3)
    @Test
    fun tc77_unavailableAnswerShowsTheMessage() {
        val fake = FakeSuggestionsRepository(unavailableAnswer)
        setSection(viewModelFor(fake))

        rule.waitUntil(5_000) { statusText() == detailText(R.string.book_detail_readalikes_unavailable) }
        rule.onAllNodesWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(0, cardCount())
    }

    // #106 TC-78 (AC4)
    @Test
    fun tc78a_notConfiguredShowsNothing() {
        val fake = FakeSuggestionsRepository(notConfiguredAnswer)
        setSection(viewModelFor(fake))

        rule.waitUntil(5_000) { fake.requests >= 1 }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReadAlikesTestTags.SECTION).assertCountEquals(0)
    }

    // #106 TC-78 (AC4)
    @Test
    fun tc78b_readyWithNoItemsShowsNothing() {
        val fake = FakeSuggestionsRepository(ready())
        setSection(viewModelFor(fake))

        rule.waitUntil(5_000) { fake.requests >= 1 }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReadAlikesTestTags.SECTION).assertCountEquals(0)
    }

    // #106 TC-78 (AC4)
    @Test
    fun tc78c_aFailedFirstReadShowsNothing() {
        val fake = FakeSuggestionsRepository(failure)
        setSection(viewModelFor(fake))

        rule.waitUntil(5_000) { fake.requests >= 1 }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReadAlikesTestTags.SECTION).assertCountEquals(0)
    }

    // #106 TC-79 (AC7)
    @Test
    fun tc79_leavingCompositionStopsPolling() {
        val fake = FakeSuggestionsRepository(pending)
        val viewModel = viewModelFor(fake)
        var shown by mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                if (shown) ReadAlikesContent(testSectionContext(book = book), viewModel)
            }
        }

        rule.waitUntil(5_000) { statusText() == detailText(R.string.book_detail_readalikes_searching) }
        val before = fake.requests
        rule.runOnIdle { shown = false }
        rule.waitForIdle()

        rule.mainClock.advanceTimeBy(30_000)
        rule.waitForIdle()

        assertEquals(before, fake.requests)
    }

    // #106 TC-80 (R8)
    @Test
    fun tc80_aNewReloadKeyReadsAgain() {
        val fake = FakeSuggestionsRepository(ready(suggestion(1)))
        val viewModel = viewModelFor(fake)
        var reloadKey by mutableIntStateOf(0)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadAlikesContent(testSectionContext(book = book, reloadKey = reloadKey), viewModel)
            }
        }

        rule.waitUntil(5_000) { cardCount() == 1 }
        assertEquals(1, fake.requests)

        rule.runOnIdle { reloadKey = 1 }
        rule.waitUntil(5_000) { fake.requests >= 2 }
        rule.waitForIdle()

        assertEquals(2, fake.requests)
        assertEquals(1, cardCount())
    }

    // #106 TC-81a (R8)
    @Test
    fun tc81a_returningWithAFinishedKeyDoesNotReadAgain() {
        val fake = FakeSuggestionsRepository(ready(suggestion(1)))
        val viewModel = viewModelFor(fake)
        var shown by mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                if (shown) ReadAlikesContent(testSectionContext(book = book), viewModel)
            }
        }
        rule.waitUntil(5_000) { cardCount() == 1 }

        rule.runOnIdle { shown = false }
        rule.waitForIdle()
        rule.runOnIdle { shown = true }
        rule.waitForIdle()

        assertEquals(1, fake.requests)
        assertEquals(1, cardCount())
    }

    // #106 TC-81b (R8)
    @Test
    fun tc81b_returningAfterAnInterruptedPollReadsAgain() {
        val fake = FakeSuggestionsRepository(pending)
        val viewModel = viewModelFor(fake)
        var shown by mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                if (shown) ReadAlikesContent(testSectionContext(book = book), viewModel)
            }
        }
        rule.waitUntil(5_000) { statusText() == detailText(R.string.book_detail_readalikes_searching) }
        val before = fake.requests

        rule.runOnIdle { shown = false }
        rule.waitForIdle()
        rule.runOnIdle { shown = true }
        rule.waitUntil(10_000) { fake.requests > before }

        assertTrue(fake.requests > before)
    }

    // #106 TC-82 (R14)
    @Test
    fun tc82_aBookWithNoCopyRendersNothingAndNeedsNoHilt() {
        val noCopy = testDetailBook(formats = emptyList())
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadAlikesSection.Content(testSectionContext(book = noCopy), Modifier)
            }
        }

        rule.waitForIdle()
        rule.onAllNodesWithTag(ReadAlikesTestTags.SECTION).assertCountEquals(0)
    }

    // #106 TC-83 (R13)
    @Test
    fun tc83_coversAreFetchedThroughTheRepositoryAndAFailedCoverKeepsTheCard() {
        // Ids and paths unique to this run, so Coil's process-wide memory cache cannot serve an earlier run.
        val run = System.nanoTime()
        val firstPath = "/api/suggestions/u$run/1/cover"
        val secondPath = "/api/suggestions/u$run/2/cover"
        val fake = FakeSuggestionsRepository(
            ready(suggestion(run, coverPath = firstPath), suggestion(run + 1, coverPath = secondPath)),
        )
        fake.coverResults[firstPath] = SuggestionCoverResult.Found(pngBytes(), "image/png")
        fake.coverResults[secondPath] = SuggestionCoverResult.NotFound
        setSection(viewModelFor(fake))

        rule.waitUntil(10_000) { fake.coverPaths.size >= 2 }

        assertTrue(fake.coverPaths.containsAll(listOf(firstPath, secondPath)))
        rule.waitUntil(5_000) { cardCount() == 2 }
        assertEquals(
            listOf(listOf("Book $run", "Author $run"), listOf("Book ${run + 1}", "Author ${run + 1}")),
            cardTexts(),
        )
    }

    // #106 TC-84 (AC2, AC3)
    @Test
    fun tc84_failedReAsksKeepTheSpinnerThenEndInUnavailable() {
        val fake = FakeSuggestionsRepository(pending, failure)
        setSection(viewModelFor(fake))
        rule.waitUntil(5_000) { statusText() == detailText(R.string.book_detail_readalikes_searching) }

        for (n in 2..6) {
            rule.mainClock.advanceTimeBy(4_000)
            rule.waitUntil(10_000) { fake.requests >= n }
            if (n < 6) assertEquals(detailText(R.string.book_detail_readalikes_searching), statusText())
        }

        rule.waitUntil(5_000) { statusText() == detailText(R.string.book_detail_readalikes_unavailable) }
        rule.onAllNodesWithTag(ReadAlikesTestTags.SPINNER, useUnmergedTree = true).assertCountEquals(0)
    }
}
