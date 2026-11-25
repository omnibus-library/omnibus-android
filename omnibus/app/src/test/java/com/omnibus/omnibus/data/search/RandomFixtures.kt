package com.omnibus.omnibus.data.search

import com.omnibus.omnibus.data.requests.randomLibraryHit
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString

/** Up to five books, with a total at or above the number shown. */
fun randomBookMatches(): SearchMatches<LibraryHit> {
    val items = List(randomInt(1..5)) { randomLibraryHit() }
    return SearchMatches(items, total = randomInt(items.size..items.size + 50))
}

fun randomLibrarySearchResults(): LibrarySearchResults = LibrarySearchResults(books = randomBookMatches())

fun randomAuthorMatch(): AuthorMatch = AuthorMatch(
    id = randomInt(1..10_000).toLong(),
    name = randomString(),
    bookCount = randomInt(1..500),
    leadBookTitle = randomString(),
)

/** Up to five authors, with a total at or above the number shown. */
fun randomAuthorMatches(): SearchMatches<AuthorMatch> {
    val items = List(randomInt(1..5)) { randomAuthorMatch() }
    return SearchMatches(items, total = randomInt(items.size..items.size + 50))
}

fun randomSeriesMatch(): SeriesMatch = SeriesMatch(
    id = randomInt(1..10_000).toLong(),
    name = randomString(),
    bookCount = randomInt(1..500),
    authorDisplay = randomString(),
)

/** Up to five series, with a total at or above the number shown. */
fun randomSeriesMatches(): SearchMatches<SeriesMatch> {
    val items = List(randomInt(1..5)) { randomSeriesMatch() }
    return SearchMatches(items, total = randomInt(items.size..items.size + 50))
}

fun randomTagMatch(): TagMatch = TagMatch(name = randomString(), bookCount = randomInt(1..500))

/** Up to five tags, with a total at or above the number shown. */
fun randomTagMatches(): SearchMatches<TagMatch> {
    val items = List(randomInt(1..5)) { randomTagMatch() }
    return SearchMatches(items, total = randomInt(items.size..items.size + 50))
}

fun randomGenreMatch(): GenreMatch = GenreMatch(name = randomString(), bookCount = randomInt(1..500))

/** Up to five genres, with a total at or above the number shown. */
fun randomGenreMatches(): SearchMatches<GenreMatch> {
    val items = List(randomInt(1..5)) { randomGenreMatch() }
    return SearchMatches(items, total = randomInt(items.size..items.size + 50))
}
