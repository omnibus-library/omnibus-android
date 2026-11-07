package com.omnibus.omnibus.feature.bookdetail.overview.chips

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.overview.chips.edit.LocalChipEditorSource
import com.omnibus.omnibus.feature.bookdetail.overview.chips.edit.NoChipEditorSource
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Genres and Tags rows: scrolling, taps and the chip role. GenresTagsScreenshotTest covers how they look. */
class GenresTagsSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val tags = GenresTagsTestTags

    private fun bookWith(genres: List<String>, subjects: List<String>): Book =
        testDetailBook().copy(genres = genres, subjects = subjects)

    private fun ComposeContentTestRule.setSection(book: Book, recorder: SectionContextRecorder = SectionContextRecorder()) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalChipEditorSource provides NoChipEditorSource) {
                    GenresTagsSection.Content(testSectionContext(book = book, recorder = recorder), Modifier.fillMaxWidth())
                }
            }
        }
        waitForIdle()
    }

    private fun chipIn(row: String, text: String): SemanticsNodeInteraction =
        rule.onNode(hasTestTag(tags.CHIP) and hasText(text) and hasAnyAncestor(hasTestTag(row)))

    // AC2
    @Test
    fun issue100_ac2_longRowScrollsSidewaysToItsLastChip() {
        val many = List(30) { "Genre number $it" }
        rule.setSection(bookWith(many, emptyList()))

        chipIn(tags.GENRES_ROW, many.last()).assertDoesNotExist()
        rule.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag(tags.GENRES_ROW)))
            .performScrollToNode(hasText(many.last()))
        chipIn(tags.GENRES_ROW, many.last()).assertIsDisplayed()
        chipIn(tags.GENRES_ROW, many.first()).assertIsNotDisplayed()
    }

    // AC3
    @Test
    fun issue100_ac3_tappingChipsNavigatesToGenreAndTagWithTheirNames() {
        val recorder = SectionContextRecorder()
        rule.setSection(bookWith(listOf("Science Fiction"), listOf("Utopia")), recorder)

        chipIn(tags.GENRES_ROW, "Science Fiction").performClick()
        chipIn(tags.TAGS_ROW, "Utopia").performClick()

        assertEquals(
            listOf(BookDetailDestination.Genre("Science Fiction"), BookDetailDestination.Tag("Utopia")),
            recorder.navigations,
        )
    }

    // AC4
    @Test
    fun issue100_ac4_chipsAreButtons() {
        rule.setSection(bookWith(listOf("Fantasy"), emptyList()))

        val chip = chipIn(tags.GENRES_ROW, "Fantasy").fetchSemanticsNode().config
        assertEquals(Role.Button, chip[SemanticsProperties.Role])
    }
}
