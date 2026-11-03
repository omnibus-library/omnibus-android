package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.geometry.Rect
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.highlights.HighlightColor
import java.util.Locale

/** The device's first locale, as the list reads it at run time. */
internal fun deviceLocale(): Locale =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]

/** Matches every highlight row node by its test tag prefix. */
internal val highlightRowMatcher = SemanticsMatcher("highlight row") {
    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(HighlightsTestTags.ROW_PREFIX) == true
}

/** A row model for tests. The date defaults to a fixed epoch millisecond value. */
internal fun testRow(
    id: Long,
    text: String? = "Passage $id",
    note: String? = null,
    color: HighlightColor = HighlightColor.Amber,
    chapterNumber: Int? = null,
    createdAtMillis: Long? = 1_761_775_445_000L,
) = HighlightRowModel(
    id = id,
    text = text,
    note = note,
    color = color,
    chapterNumber = chapterNumber,
    createdAtMillis = createdAtMillis,
)

/** The dp count of a pixel distance in this rule's density. */
internal fun ComposeContentTestRule.dpOf(px: Float): Float = px / density.density

/** The pixel count of [dp] in this rule's density. */
internal fun ComposeContentTestRule.pxOf(dp: Float): Float = dp * density.density

/** Whether [inner] lies within [outer], allowing [slopPx] of tolerance. */
internal fun inside(inner: Rect, outer: Rect, slopPx: Float = 0.5f): Boolean =
    inner.left >= outer.left - slopPx && inner.right <= outer.right + slopPx &&
        inner.top >= outer.top - slopPx && inner.bottom <= outer.bottom + slopPx

/** A row's footer text, computed as the list builds it: "Chapter N · <date>" with whichever parts it has. */
internal fun expectedFooter(chapterNumber: Int?, createdAtMillis: Long?, nowMillis: Long): String? {
    val parts = listOfNotNull(
        chapterNumber?.let { detailChapter(it) },
        createdAtMillis?.let {
            com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat.shortDate(
                it,
                nowMillis,
                deviceLocale(),
                java.util.TimeZone.getDefault(),
            )
        },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** "Chapter N" in the app's string. */
internal fun detailChapter(number: Int): String =
    com.omnibus.omnibus.feature.bookdetail.detailText(
        com.omnibus.omnibus.R.string.book_detail_highlights_chapter,
        number,
    )
