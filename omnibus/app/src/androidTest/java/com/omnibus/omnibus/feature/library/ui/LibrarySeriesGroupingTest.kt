package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The group-by-series switch. */
class LibrarySeriesGroupingTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc235_22_pillIsOneSwitchNamedGroupBySeries() {
        setLibrary(mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))))

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertContentDescriptionEquals(GROUP_LABEL)
    }

    @Test
    fun tc235_22_pillIsOffWhenGroupingIsOff() {
        setLibrary(mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))))

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).assertIsOff()
    }

    @Test
    fun tc235_22_pillIsOnWhenGroupingIsOn() {
        setLibrary(mutableStateOf(groupedState(book(1L), book(2L))))

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).assertIsOn()
    }

    @Test
    fun tc235_22_tappingPillCallsToggle() {
        var toggles = 0
        setLibrary(
            mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))),
            onToggleGroupBySeries = { toggles++ },
        )

        rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun tc235_22_pillSitsLeftOfFilterButton() {
        setLibrary(mutableStateOf(LibraryUIState(books = listOf(book(1L), book(2L)))))

        val pill = rule.onNodeWithTag(LibraryTestTags.GROUP_SWITCH).getUnclippedBoundsInRoot()
        val filter = rule.onNodeWithTag(LibraryTestTags.FILTER_BUTTON).getUnclippedBoundsInRoot()

        assertTrue(pill.right <= filter.left)
    }

    private fun setLibrary(
        uiState: State<LibraryUIState>,
        onLoadMore: (Int) -> Unit = {},
        onToggleGroupBySeries: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    LibraryScreenContent(
                        uiState = uiState.value,
                        onRetry = {},
                        onRefresh = {},
                        onLoadMore = onLoadMore,
                        onOpenBook = {},
                        onAddBooks = {},
                        onTransientErrorShown = {},
                        onToggleGroupBySeries = onToggleGroupBySeries,
                    )
                }
            }
        }
    }

    private companion object {
        const val GROUP_LABEL = "Group by series"

        fun groupedState(vararg books: Book) = LibraryUIState(books = books.toList(), groupBySeries = true)

        /** A book with a uuid of `uuid-<id>` and no cover, so no image loads in these tests. */
        fun book(id: Long) = Book(
            id = id,
            uniqueIdentifier = "uuid-$id",
            filename = "book-$id.epub",
            title = "Book $id",
            creators = listOf(Creator("Author $id", null, null, null)),
            coverUrl = null,
            accent = null,
            formats = listOf("epub"),
            series = null,
            seriesIndex = null,
            hasPhysical = false,
            addedAt = null,
            lastInteractedAt = null,
            bookFiles = emptyList(),
        )

    }
}
