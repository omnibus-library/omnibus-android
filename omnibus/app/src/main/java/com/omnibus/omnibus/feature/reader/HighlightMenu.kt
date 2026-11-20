package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.reader.ui.AnnotationTapData
import com.omnibus.omnibus.feature.reader.ui.PageRect

/**
 * A saved highlight the menu can show: its colour, the text Copy takes (null when there is none), whether it carries a
 * note, which makes Delete ask first, and the note's text, null when it has none (it fills the note screen in edit
 * mode).
 */
data class KnownHighlight(val color: HighlightColor, val text: String?, val hasNote: Boolean, val note: String? = null)

/**
 * One highlight in the menu: its trimmed range, where the page draws it, its colour, its text, whether it carries a
 * note, which makes Delete ask first, and the note's text, null when it has none (it fills the note screen in edit
 * mode).
 */
data class HighlightMenuEntry(
    val cfiRange: String,
    val rects: List<PageRect>,
    val color: HighlightColor,
    val text: String?,
    val hasNote: Boolean,
    val note: String? = null,
)

/**
 * The highlights a tap landed on, with [active] the one the menu shows. [stepped] moves between them and wraps at the
 * ends. Always holds at least one entry.
 */
data class HighlightMenu(val entries: List<HighlightMenuEntry>, val active: Int = 0) {
    init {
        require(entries.isNotEmpty() && active in entries.indices)
    }

    /** The entry the menu shows. */
    val activeEntry: HighlightMenuEntry get() = entries[active]

    /** How many highlights the tap landed on. */
    val count: Int get() = entries.size

    /** The active entry's 1-based place among [count]. */
    val position: Int get() = active + 1

    /** Whether the menu offers a way to move to another highlight. */
    val hasSwitcher: Boolean get() = entries.size > 1

    /** The menu with the entry [delta] places away active, wrapping past either end. */
    fun stepped(delta: Int): HighlightMenu = copy(active = Math.floorMod(active + delta, entries.size))
}

/**
 * The menu for [tap]: every hit the page reported that is in [known], in the page's order, once per range. A hit with
 * no rects, or with a range [known] does not have, is dropped. Null when none is left. [known] is keyed by the trimmed
 * range.
 */
fun highlightMenuOf(tap: AnnotationTapData, known: Map<String, KnownHighlight>): HighlightMenu? {
    val seen = mutableSetOf<String>()
    val entries = tap.allHits.mapNotNull { hit ->
        val cfi = hit.cfiRange?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (hit.rects.isEmpty()) return@mapNotNull null
        val highlight = known[cfi] ?: return@mapNotNull null
        if (!seen.add(cfi)) return@mapNotNull null
        HighlightMenuEntry(cfi, hit.rects, highlight.color, highlight.text, highlight.hasNote, highlight.note)
    }
    return entries.takeIf { it.isNotEmpty() }?.let { HighlightMenu(it) }
}
