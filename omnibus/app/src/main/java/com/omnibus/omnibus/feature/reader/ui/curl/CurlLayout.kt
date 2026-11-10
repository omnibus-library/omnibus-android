package com.omnibus.omnibus.feature.reader.ui.curl

/** How a curl is laid out over the stage: one page, or a two-page spread that lifts only its right page. */
internal enum class CurlLayout {
    Single,
    Spread;

    /**
     * Where a curl's flap may show over [bounds]: the whole stage for a single page, and for a spread the widest band
     * centred on [spine]. A spine that is not inside [bounds] gives the whole stage.
     */
    fun flapBounds(bounds: CurlRect, spine: Float?): CurlRect = when (this) {
        Single -> bounds
        Spread -> spineInside(bounds, spine)?.let { s ->
            val half = minOf(s - bounds.left, bounds.right - s)
            CurlRect(s - half, bounds.top, s + half, bounds.bottom)
        } ?: bounds
    }

    /** The page that lifts over [bounds]: the whole stage, or a spread's right page from [spine] to the right edge. */
    fun turning(bounds: CurlRect, spine: Float?): CurlRect = when (this) {
        Single -> bounds
        Spread -> spineInside(bounds, spine)?.let { CurlRect(it, bounds.top, bounds.right, bounds.bottom) } ?: bounds
    }

    companion object {
        /** The layout for [columns] as the glue reports it, or null for any other count, which slides. */
        fun of(columns: Int?): CurlLayout? = when (columns) {
            1 -> Single
            2 -> Spread
            else -> null
        }

        private fun spineInside(bounds: CurlRect, spine: Float?): Float? =
            spine?.takeIf { it > bounds.left && it < bounds.right }
    }
}
