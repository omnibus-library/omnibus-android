package com.omnibus.omnibus.feature.bookdetail.files.details

private const val ELLIPSIS = "…"

/**
 * [text] cut in the middle to the most it can keep while [fits] still holds, as "Start of a lo…end.epub". Compose's
 * middle ellipsis falls back to clipping on more than one line, so the cut is made here against the real layout.
 * Returns [text] when it already fits, and [ELLIPSIS] alone when not even one character does. [fits] must only get
 * harder as more characters are kept.
 */
internal fun fitMiddle(text: String, fits: (String) -> Boolean): String {
    if (fits(text)) return text
    var low = 0 // kept characters known to fit
    var high = text.length // kept characters known not to fit
    while (high - low > 1) {
        val mid = (low + high) / 2
        if (fits(cutMiddle(text, mid))) low = mid else high = mid
    }
    return cutMiddle(text, low)
}

/** [keep] characters of [text], the first half then the last half, around an ellipsis, never splitting a surrogate pair. */
internal fun cutMiddle(text: String, keep: Int): String {
    val tail = keep / 2
    var head = keep - tail
    if (head in 1 until text.length && text[head - 1].isHighSurrogate()) head--
    var tailStart = text.length - tail
    if (tailStart in 1 until text.length && text[tailStart].isLowSurrogate()) tailStart++
    return text.substring(0, head) + ELLIPSIS + text.substring(tailStart)
}
