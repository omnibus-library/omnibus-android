package com.omnibus.omnibus.data.search

import com.omnibus.omnibus.data.requests.randomLibraryHit
import com.omnibus.omnibus.testing.randomInt

/** Up to five books, with a total at or above the number shown. */
fun randomBookMatches(): SearchMatches<LibraryHit> {
    val items = List(randomInt(1..5)) { randomLibraryHit() }
    return SearchMatches(items, total = randomInt(items.size..items.size + 50))
}

fun randomLibrarySearchResults(): LibrarySearchResults = LibrarySearchResults(books = randomBookMatches())
