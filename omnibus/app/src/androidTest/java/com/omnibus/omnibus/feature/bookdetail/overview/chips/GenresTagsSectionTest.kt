package com.omnibus.omnibus.feature.bookdetail.overview.chips

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.overview.chips.edit.LocalChipEditorSource
import com.omnibus.omnibus.feature.bookdetail.overview.chips.edit.NoChipEditorSource
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Genres and Tags rows of the Overview panel: which rows show, scrolling, touch size, taps and reading order. */
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

    // AC1
    @Test
    fun issue100_ac1_bookWithGenresAndTagsShowsBothRowsWithTheirChips() {
        rule.setSection(bookWith(listOf("Science Fiction", "Dystopia"), listOf("Utopia", "Anthropology")))

        rule.onNodeWithTag(tags.GENRES_ROW).assertIsDisplayed()
        rule.onNodeWithTag(tags.TAGS_ROW).assertIsDisplayed()
        rule.onNode(hasText(detailText(R.string.book_detail_chips_genres)) and hasAnyAncestor(hasTestTag(tags.GENRES_ROW)))
            .assertIsDisplayed()
        rule.onNode(hasText(detailText(R.string.book_detail_chips_tags)) and hasAnyAncestor(hasTestTag(tags.TAGS_ROW)))
            .assertIsDisplayed()
        chipIn(tags.GENRES_ROW, "Science Fiction").assertIsDisplayed()
        chipIn(tags.GENRES_ROW, "Dystopia").assertIsDisplayed()
        chipIn(tags.TAGS_ROW, "Utopia").assertIsDisplayed()
        chipIn(tags.TAGS_ROW, "Anthropology").assertIsDisplayed()
    }

    // AC1
    @Test
    fun issue100_ac1_tagsRowWithoutValuesIsNotShown() {
        rule.setSection(bookWith(listOf("Fantasy"), emptyList()))
        rule.onNodeWithTag(tags.GENRES_ROW).assertIsDisplayed()
        rule.onAllNodesWithTag(tags.TAGS_ROW).assertCountEquals(0)
    }

    // AC1
    @Test
    fun issue100_ac1_genresRowWithoutValuesIsNotShown() {
        rule.setSection(bookWith(emptyList(), listOf("Epic")))
        rule.onAllNodesWithTag(tags.GENRES_ROW).assertCountEquals(0)
        rule.onNodeWithTag(tags.TAGS_ROW).assertIsDisplayed()
    }

    // AC1
    @Test
    fun issue100_ac1_bookWithNeitherShowsNothing() {
        rule.setSection(bookWith(emptyList(), emptyList()))
        rule.onAllNodesWithTag(tags.GENRES_ROW).assertCountEquals(0)
        rule.onAllNodesWithTag(tags.TAGS_ROW).assertCountEquals(0)
        rule.onAllNodesWithTag(tags.CHIP).assertCountEquals(0)
    }

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
    fun issue100_ac4_labelPrecedesItsChipsInReadingOrderAndChipsAreButtons() {
        rule.setSection(bookWith(listOf("Fantasy", "Epic"), listOf("Dragons")))

        val label = rule.onNode(hasText(detailText(R.string.book_detail_chips_genres))).bounds()
        val first = chipIn(tags.GENRES_ROW, "Fantasy").bounds()
        val second = chipIn(tags.GENRES_ROW, "Epic").bounds()
        assertTrue(label.right <= first.left + 1 && first.left < second.left)

        val chip = chipIn(tags.GENRES_ROW, "Fantasy").fetchSemanticsNode().config
        assertEquals(Role.Button, chip[SemanticsProperties.Role])
        chipIn(tags.GENRES_ROW, "Fantasy").assertTextEquals("Fantasy")
    }
}
