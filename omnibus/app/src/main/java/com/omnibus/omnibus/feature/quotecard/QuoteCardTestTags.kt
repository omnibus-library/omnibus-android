package com.omnibus.omnibus.feature.quotecard

/** Test tags for the quote card sheet's controls. */
internal object QuoteCardTestTags {
    const val SHEET = "quote-card-sheet"
    const val DONE = "quote-card-done"
    const val PREVIEW = "quote-card-preview"
    const val SHARE = "quote-card-share"
    const val COPY = "quote-card-copy"
    const val KIND_PILL = "quote-card-kind-pill"
    const val KIND_MENU = "quote-card-kind-menu"
    const val SWATCH_ROW = "quote-card-swatch-row"
    const val DIVIDER = "quote-card-swatch-divider"

    fun kindItem(kind: QuoteBackgroundKind) = "quote-card-kind-${kind.name.lowercase()}"
}
