package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.omnibus.omnibus.data.highlights.HighlightColor

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
