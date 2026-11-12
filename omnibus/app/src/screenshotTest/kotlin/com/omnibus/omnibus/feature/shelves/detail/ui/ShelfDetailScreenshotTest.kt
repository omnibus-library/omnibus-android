package com.omnibus.omnibus.feature.shelves.detail.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.FilterMode
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.feature.shelves.detail.ShelfDetailUIState
import com.omnibus.omnibus.feature.shelves.detail.ShelfViewer
import com.omnibus.omnibus.screenshot.Shot

/** A populated smart shelf in the dark palette: its hero with rules, an active facet with its N of M line, and a series stack in the grid. */
@PreviewTest
@Preview(name = "Dark", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelfDetailPopulatedSmartDark() {
    Shot {
        ShelfDetailContent(
            uiState = populatedSmartShelf(),
            onBack = {},
            onOpenBook = {},
            onOpenSeries = { _, _ -> },
            onRefresh = {},
            onRetry = {},
            onUnfoldSeries = {},
            onFoldUpSeries = {},
            onTransientErrorShown = {},
            gridState = rememberLazyGridState(),
            reducedMotion = true,
        )
    }
}

/** An empty hand-picked shelf for an editor, in the dark palette: the empty plate and the Add books action. */
@PreviewTest
@Preview(name = "Dark empty", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelfDetailEmptyHandPickedDark() {
    Shot {
        ShelfDetailContent(
            uiState = emptyHandPickedShelf(),
            onBack = {},
            onOpenBook = {},
            onOpenSeries = { _, _ -> },
            onRefresh = {},
            onRetry = {},
            onUnfoldSeries = {},
            onFoldUpSeries = {},
            onTransientErrorShown = {},
            gridState = rememberLazyGridState(),
            reducedMotion = true,
        )
    }
}

private fun populatedSmartShelf(): ShelfDetailUIState {
    val shelf = shelf(id = 1L, kind = ShelfKind.Smart, visibility = ShelfVisibility.Public, name = "Weekend reads", count = 14L)
    val books = listOf(
        book(1L, seriesId = 10L, seriesIndex = "1"),
        book(2L, seriesId = 10L, seriesIndex = "2"),
        book(3L),
        book(4L),
        book(5L),
        book(6L),
    )
    return ShelfDetailUIState(
        shelfId = shelf.id,
        preview = ShelfPreview(shelf, listOf("cover-1", "cover-2", "cover-3", "cover-4")),
        detail = ShelfDetail(
            shelf = shelf,
            description = "Slow books for rainy afternoons.",
            rules = ShelfRules(
                matchMode = ShelfMatchMode.All,
                rules = listOf(
                    ShelfRule(ShelfRuleField.Rating, ShelfRuleOp.Is, "5"),
                    ShelfRule(ShelfRuleField.Genre, ShelfRuleOp.Is, "Poetry"),
                ),
            ),
        ),
        books = books.take(4),
        unfilteredBooks = books,
        clauses = listOf(FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy"))),
    )
}

private fun emptyHandPickedShelf(): ShelfDetailUIState {
    val shelf = shelf(id = 2L, kind = ShelfKind.Manual, visibility = ShelfVisibility.Private, name = "Weekend reads", count = 0L)
    return ShelfDetailUIState(
        shelfId = shelf.id,
        preview = ShelfPreview(shelf, emptyList()),
        detail = ShelfDetail(shelf, description = null, rules = ShelfRules(null, emptyList())),
        books = emptyList(),
        unfilteredBooks = emptyList(),
        viewer = ShelfViewer(userId = shelf.ownerUserId, isAdmin = false),
    )
}

private fun shelf(
    id: Long,
    kind: ShelfKind,
    visibility: ShelfVisibility,
    name: String,
    count: Long,
): Shelf = Shelf(
    id = id,
    ownerUserId = 7L,
    ownerUsername = "Hunter",
    kind = kind,
    name = name,
    visibility = visibility,
    accent = null,
    bookCount = count,
)

private fun book(id: Long, seriesId: Long? = null, seriesIndex: String? = null): Book = Book(
    id = id,
    uniqueIdentifier = "cover-$id",
    filename = "book-$id.epub",
    title = "Book $id",
    creators = emptyList(),
    coverUrl = null,
    accent = null,
    formats = listOf("epub"),
    series = seriesId?.let { "Series $it" },
    seriesIndex = seriesIndex,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
    seriesId = seriesId,
)
