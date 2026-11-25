package com.omnibus.omnibus.feature.search

import android.content.res.Configuration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.feature.search.rows.FormatBadge
import com.omnibus.omnibus.feature.search.rows.SearchBookRow
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity

/** Books for a "wood" search. Covers are absent, so each row draws its generated plate. */
internal val woodHits = listOf(
    LibraryHit("u-1", "Norwegian Wood", "Haruki Murakami", "1987", listOf("epub", "m4b"), null, null),
    LibraryHit("u-2", "Mythago Wood", "Robert Holdstock", "1984", listOf("epub"), null, null, hasPhysical = true),
    LibraryHit("u-3", "The Wood Wife", "Terri Windling", null, listOf("epub"), null, null),
    LibraryHit("u-4", "Lavondyss: Journey into Mythago Wood", "Robert Holdstock", "1988", listOf("m4b"), null, null),
    LibraryHit("u-5", "The Woodcutter", "Kate Danley", "2011", listOf("epub", "m4b", "pdf"), null, null, hasPhysical = true),
    LibraryHit(
        "u-6",
        "A very long title about woods, the paths through them and the things that never stop growing there",
        "",
        null,
        emptyList(),
        null,
        null,
    ),
)

private fun results(query: String, hits: List<LibraryHit> = woodHits, total: Int = hits.size) = SearchUIState(
    query = query,
    body = SearchBody.Results(
        query = query,
        sections = listOf(SearchSection(SearchSectionKind.Books, SearchMatches(hits, total))),
    ),
)

@PreviewTest
@PreviewLightDark
@Composable
fun SearchPrompt() {
    Shot {
        SearchScreenContent(SearchUIState(), {}, {}, {}, {}, {})
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun SearchResults() {
    Shot {
        SearchScreenContent(results("wood", total = 9), {}, {}, {}, {}, {})
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun SearchResultsScrolled() {
    Shot {
        SearchScreenContent(
            results("wood"),
            {},
            {},
            {},
            {},
            {},
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 2),
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun SearchNoMatches() {
    Shot {
        SearchScreenContent(
            SearchUIState(query = "woodx", body = SearchBody.NoMatches("woodx")),
            {},
            {},
            {},
            {},
            {},
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun SearchUnreachable() {
    Shot {
        SearchScreenContent(
            SearchUIState(query = "wood", body = SearchBody.Failed(SearchFailure.Unreachable)),
            {},
            {},
            {},
            {},
            {},
        )
    }
}

/** A tablet-sized window: one 560dp column centred beside the rail. */
@PreviewTest
@Preview(name = "Light", widthDp = 900, heightDp = 600)
@Preview(name = "Dark", widthDp = 900, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SearchWide() {
    Shot {
        SearchScreenContent(results("wood"), {}, {}, {}, {}, {})
    }
}

/** The largest font scale on a narrow window: the title and byline stay one line each, and the badges wrap. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, fontScale = 2f)
@Preview(name = "Dark", widthDp = 360, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SearchBookRowLargestFont() {
    Shot {
        SearchBookRow(
            title = AnnotatedString("Lavondyss: Journey into Mythago Wood and the Other Stories of the Forest"),
            byline = AnnotatedString("Robert Holdstock and a Very Long Co-author Name · 1988"),
            badges = listOf(FormatBadge.Ebook, FormatBadge.Audio, FormatBadge.Print),
            coverIdentity = CoverIdentity(uuid = "u-4", title = "Lavondyss", hasCover = false),
            description = "Book, Lavondyss, Robert Holdstock, 1988, ebook, audio, print",
            onClick = {},
        )
    }
}
