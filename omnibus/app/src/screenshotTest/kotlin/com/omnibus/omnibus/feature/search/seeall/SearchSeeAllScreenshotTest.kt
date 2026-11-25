package com.omnibus.omnibus.feature.search.seeall

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.feature.search.SearchSectionKind
import com.omnibus.omnibus.feature.search.woodHits
import com.omnibus.omnibus.screenshot.Shot

private val seeAllRoute = SearchSeeAllRoute(SearchSectionKind.Books, "wood")

@PreviewTest
@PreviewLightDark
@Composable
fun SeeAllBooks() {
    val hits = woodHits.take(5) + listOf(
        LibraryHit("u-7", "Into the Woods", "Tana French", "2007", listOf("epub"), null, null),
        LibraryHit("u-8", "The Woodlanders", "Thomas Hardy", "1887", listOf("epub"), null, null, hasPhysical = true),
    )
    Shot {
        SearchSeeAllScreenContent(
            route = seeAllRoute,
            uiState = SearchSeeAllUIState(SeeAllBody.Loaded(SearchMatches(hits, total = 7))),
            onBack = {},
            onRetry = {},
            onOpen = {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun SeeAllCapped() {
    Shot {
        SearchSeeAllScreenContent(
            route = seeAllRoute,
            uiState = SearchSeeAllUIState(SeeAllBody.Loaded(SearchMatches(woodHits, total = 1200))),
            onBack = {},
            onRetry = {},
            onOpen = {},
        )
    }
}
