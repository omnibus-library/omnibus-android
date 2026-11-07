package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.ui.theme.OmnibusTheme

// The sheet is always dark, so each state has one shot.

/** The sheet's container colour. */
private val SheetContainer = Color(0xFF15120F)

@PreviewTest
@Preview
@Composable
fun ChipEditSheetGenres() {
    ChipEditShot(state(genres = listOf("Noir", "Epic", "Dystopia")))
}

@PreviewTest
@Preview
@Composable
fun ChipEditSheetEmpty() {
    ChipEditShot(state())
}

/** Five suggestions with their book counts, leaving out "cozy", which is a current chip. */
@PreviewTest
@Preview
@Composable
fun ChipEditSheetSuggestions() {
    val pool = listOf(
        "Alpha" to 1, "Beta" to 4, "cozy" to 3, "Gamma" to 9, "Delta" to 2, "Epsilon" to 7, "Zeta" to 3, "Eta" to 5,
    )
    ChipEditShot(
        state(
            genres = listOf("Cozy"),
            pools = mapOf(MetadataListKind.Genres to PoolState.Loaded(pool.map { (name, count) -> VocabularyEntry(name, count.toLong()) })),
        ),
    )
}

/** With the pool still loading there are no suggestions, but the create row shows. */
@PreviewTest
@Preview
@Composable
fun ChipEditSheetPoolLoading() {
    ChipEditShot(state(entry = "Grimdark", pools = mapOf(MetadataListKind.Genres to PoolState.Loading)))
}

private fun state(
    genres: List<String> = emptyList(),
    entry: String = "",
    pools: Map<MetadataListKind, PoolState> = emptyMap(),
) = ChipEditUIState(canEdit = true, isOnline = true, bound = true, genres = genres, tags = emptyList(), entry = entry, pools = pools)

@Composable
private fun ChipEditShot(state: ChipEditUIState) {
    OmnibusTheme(darkTheme = true) {
        Box(Modifier.background(SheetContainer).padding(16.dp)) {
            ChipEditSheetContent(
                state = state,
                kind = MetadataListKind.Genres,
                bookTitle = "The Left Hand of Darkness",
                inlineError = null,
                onRemove = {},
                onAdd = {},
                onEntryChange = {},
            )
        }
    }
}
