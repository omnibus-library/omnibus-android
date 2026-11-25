package com.omnibus.omnibus.feature.search.rows

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.data.search.TagMatch
import com.omnibus.omnibus.feature.search.SearchSectionKind
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllRoute
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllScreenContent
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllUIState
import com.omnibus.omnibus.feature.search.seeall.SeeAllBody
import com.omnibus.omnibus.screenshot.Shot

/** Tags for a "wood" search: a short name, a singular count, and a long name with the query inside it. */
@PreviewTest
@PreviewLightDark
@Composable
fun SearchTagRows() {
    Shot {
        Column {
            SearchTagRow(TagMatch("Woodland", 14), "wood", onOpen = {})
            SearchTagRow(TagMatch("Woodworking", 1), "wood", onOpen = {})
            SearchTagRow(TagMatch("Folk tales of the wildwood and the deep wood", 212), "wood", onOpen = {})
        }
    }
}

/** The largest font scale on a narrow window: the name and the count each stay on one line. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, fontScale = 2f)
@Preview(name = "Dark", widthDp = 360, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SearchTagRowLargestFont() {
    Shot {
        SearchTagRow(TagMatch("Folk tales of the wildwood and the deep wood", 212), "wood", onOpen = {})
    }
}

/** The Tags See all list past the cap: the capped line over the first rows, with the query highlighted. */
@PreviewTest
@PreviewLightDark
@Composable
fun SeeAllTagsCapped() {
    val tags = listOf(
        TagMatch("Adventure", 212),
        TagMatch("Fantasy", 188),
        TagMatch("Historical", 140),
        TagMatch("Magic", 131),
        TagMatch("Romance", 97),
        TagMatch("Space opera", 88),
        TagMatch("Dark academia", 61),
        TagMatch("Mythology", 57),
        TagMatch("Translated", 44),
    )
    Shot {
        SearchSeeAllScreenContent(
            route = SearchSeeAllRoute(SearchSectionKind.Tags, "a"),
            uiState = SearchSeeAllUIState(SeeAllBody.Loaded(SearchMatches(tags, total = 812))),
            onBack = {},
            onRetry = {},
            onOpen = {},
        )
    }
}
