package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.suggestions.TestSuggestionsRepository
import com.omnibus.omnibus.data.suggestions.SuggestionCoverResult
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

    private fun viewModelFor(fake: TestSuggestionsRepository) = ReadAlikesViewModel("uuid-1", fake)

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

    /** A small valid PNG, made at run time so the test needs no resource file. */
    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    // #106 TC-76 (R9)
    @Test
    fun tc76_tappingACardNavigatesToItsHardcoverUrl() {
        val recorder = SectionContextRecorder()
        val second = suggestion(2)
        val fake = TestSuggestionsRepository(ready(suggestion(1), second))
        setSection(viewModelFor(fake), recorder = recorder)

        rule.waitUntil(5_000) { cardCount() == 2 }
        rule.onAllNodesWithTag(BookShelfTestTags.CARD)[1].performClick()
        rule.waitForIdle()

        assertEquals(listOf<BookDetailDestination>(BookDetailDestination.ExternalUrl(second.hardcoverUrl)), recorder.navigations)
    }

    // #106 TC-79 (AC7)
    @Test
    fun tc79_leavingCompositionStopsPolling() {
        val fake = TestSuggestionsRepository(pending)
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
        val fake = TestSuggestionsRepository(ready(suggestion(1)))
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
    }

    // #106 TC-83 (R13)
    @Test
    fun tc83_coversAreFetchedThroughTheRepository() {
        // Ids and paths unique to this run, so Coil's process-wide memory cache cannot serve an earlier run.
        val run = System.nanoTime()
        val firstPath = "/api/suggestions/u$run/1/cover"
        val secondPath = "/api/suggestions/u$run/2/cover"
        val fake = TestSuggestionsRepository(
            ready(suggestion(run, coverPath = firstPath), suggestion(run + 1, coverPath = secondPath)),
        )
        fake.coverResults[firstPath] = SuggestionCoverResult.Found(pngBytes(), "image/png")
        fake.coverResults[secondPath] = SuggestionCoverResult.NotFound
        setSection(viewModelFor(fake))

        rule.waitUntil(10_000) { fake.coverPaths.size >= 2 }

        assertTrue(fake.coverPaths.containsAll(listOf(firstPath, secondPath)))
    }
}
