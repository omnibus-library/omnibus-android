package com.omnibus.omnibus.feature.reader.ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The lenient JSON the page's payloads are decoded with. Unknown keys are ignored, and a null or
 * missing value takes the property's default.
 */
internal val ReaderJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * The `relocate` payload: where the reader is in the book.
 *
 * [pct] is a whole percentage from 0 to 100, and [chapter] is 0 when no TOC entry matched, as on
 * iOS. [echo] is true when the page moved without the reader doing anything, such as a rotation's
 * re-pagination.
 */
@Serializable
data class RelocateData(
    val cfi: String? = null,
    val page: Int = 0,
    val totalPages: Int = 0,
    val pct: Int = 0,
    val atEnd: Boolean = false,
    val chapter: Int = 0,
    val totalChapters: Int = 0,
    val chapterTitle: String = "",
    val chapterPagesLeft: Int = 0,
    val echo: Boolean = false,
    val settledSeek: Int? = null,
) {
    /** True when the reader moved, as opposed to the page re-settling on its own. */
    val isMovement: Boolean get() = !echo

    /** [pct] as a fraction from 0.0 to 1.0. */
    val fraction: Double get() = pct / 100.0
}

/** One row of the table of contents. [page] and [pct] arrive only on the second `toc` emission. */
@Serializable
data class TocEntry(
    val label: String = "",
    val href: String = "",
    val level: Int = 0,
    val page: Int? = null,
    val pct: Int? = null,
)

/** A selection rectangle in page CSS pixels. */
@Serializable
data class PageRect(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
)

/** A selection drag handle's anchor in page CSS pixels. */
@Serializable
data class SelectionHandle(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val height: Double = 0.0,
)

/**
 * The `selection` payload. [existing] stays raw until #55 defines the annotation shape, so it is
 * the overlapping annotation as JSON, or null.
 */
@Serializable
data class SelectionData(
    val cfiRange: String? = null,
    val text: String = "",
    val rects: List<PageRect> = emptyList(),
    val start: SelectionHandle? = null,
    val end: SelectionHandle? = null,
    val existing: JsonElement? = null,
    val dragging: Boolean = false,
)

/** The `annotationTap` payload: the tapped highlight's range and where it is drawn. */
@Serializable
data class AnnotationTapData(
    val cfiRange: String? = null,
    val rects: List<PageRect> = emptyList(),
)

/** The `shareImage` payload: a quote card's file name and its image as a data URL. */
@Serializable
data class ShareImageData(
    val name: String = "",
    val dataUrl: String = "",
)
