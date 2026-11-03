package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.metadata.VocabularyEntry
import java.util.Locale

/** The most chips one book can carry, as the server allows. */
internal const val MAX_CHIPS = 64

/** The longest chip, in characters, as the server allows. */
internal const val MAX_CHIP_LENGTH = 128

/** The most suggestions shown under the field. */
internal const val MAX_SUGGESTIONS = 5

/** The pure rules for the chip editor's suggestions and its add-as-new row. A port of iOS `SuggestionPool`. */
internal object SuggestionPool {

    /**
     * The library's values with case-insensitive repeats merged: the first spelling wins and the higher count is kept.
     * Sorted by lowercased name.
     */
    fun collect(entries: List<VocabularyEntry>): List<VocabularyEntry> =
        entries
            .groupBy { it.name.lowercase(Locale.ROOT) }
            .map { (_, same) -> VocabularyEntry(name = same.first().name, count = same.maxOf { it.count }) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

    /**
     * Up to [MAX_SUGGESTIONS] entries of [pool] that contain the trimmed [query] ignoring case, leaving out values
     * already in [current]. An empty query gives the head of the pool.
     */
    fun filtered(pool: List<VocabularyEntry>, current: List<String>, query: String): List<VocabularyEntry> {
        val q = query.trim()
        return pool
            .filterNot { entry -> current.any { it.equals(entry.name, ignoreCase = true) } }
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
    }

    /** Whether [typed], trimmed, is text that no pool value or current chip equals, so the add-as-new row shows. */
    fun showsCreateRow(pool: List<VocabularyEntry>, current: List<String>, typed: String): Boolean {
        val t = typed.trim()
        return t.isNotEmpty() &&
            pool.none { it.name.equals(t, ignoreCase = true) } &&
            current.none { it.equals(t, ignoreCase = true) }
    }

    /** The chip [typed] would add, trimmed, or null when it is blank, repeats a current chip, or breaks a limit. */
    fun committedChip(typed: String, existing: List<String>): String? {
        val t = typed.trim()
        return when {
            t.isEmpty() -> null
            t.length > MAX_CHIP_LENGTH -> null
            existing.size >= MAX_CHIPS -> null
            existing.any { it.equals(t, ignoreCase = true) } -> null
            else -> t
        }
    }
}
