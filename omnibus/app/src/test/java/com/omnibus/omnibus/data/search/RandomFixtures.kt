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

fun randomTagMatch(): TagMatch = TagMatch(name = randomString(), bookCount = randomInt(1..500))

/** Up to five tags, with a total at or above the number shown. */
fun randomTagMatches(): SearchMatches<TagMatch> {
    val items = List(randomInt(1..5)) { randomTagMatch() }
    return SearchMatches(items, total = randomInt(items.size..items.size + 50))
}
