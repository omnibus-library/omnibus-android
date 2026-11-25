package com.omnibus.omnibus.feature.search.highlight

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * [text] with every case-insensitive occurrence of each whitespace-separated word of [query] drawn in [style].
 * Overlapping or touching matches become one span. A blank [query] or no match returns plain text.
 */
fun highlightMatches(query: String, text: String, style: SpanStyle): AnnotatedString {
    val words = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
    // regionMatches compares char by char, so indices never shift the way lowercase() can (e.g. "İ").
    val ranges = words.flatMap { word ->
        (0..text.length - word.length)
            .filter { text.regionMatches(it, word, 0, word.length, ignoreCase = true) }
            .map { it until it + word.length }
    }.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (range in ranges) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return buildAnnotatedString {
        append(text)
        merged.forEach { addStyle(style, it.first, it.last + 1) }
    }
}
