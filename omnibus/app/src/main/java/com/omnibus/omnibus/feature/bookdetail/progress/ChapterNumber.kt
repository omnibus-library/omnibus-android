package com.omnibus.omnibus.feature.bookdetail.progress

import kotlin.math.abs

/** Chapter starts closer than this are the same start (C8's tolerance). */
internal const val CHAPTER_START_EPSILON = 1e-4f

/**
 * The 1-based chapter number at [fraction], or null when it can't be placed. Chapters that share the start of the hit
 * give the lowest index, as the server's `chapter_title` does, because [chapterAt] alone returns the later one.
 */
internal fun chapterNumberAt(fraction: Float?, chapters: List<BookChapter>): Int? {
    val f = fraction?.takeIf { !it.isNaN() } ?: return null
    val hit = chapters.chapterAt(f) ?: return null
    return chapters
        .filter { abs(it.startFraction - hit.startFraction) <= CHAPTER_START_EPSILON }
        .minOf { it.index } + 1
}
