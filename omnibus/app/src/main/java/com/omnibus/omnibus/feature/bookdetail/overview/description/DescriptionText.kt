package com.omnibus.omnibus.feature.bookdetail.overview.description

/** How many lines the description shows until the reader asks for more. */
internal const val DESCRIPTION_CLAMP_LINES = 3

/**
 * Whether the description needs a "Show more" toggle. Only text whose full layout takes more lines than the clamp
 * gets one, so a description that fits exactly never offers a toggle that changes nothing.
 */
internal fun showsDescriptionToggle(fullLineCount: Int, clampLines: Int = DESCRIPTION_CLAMP_LINES): Boolean =
    fullLineCount > clampLines

private val LineBreakTag = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val LineEndTag = Regex("</(div|li)\\s*>", RegexOption.IGNORE_CASE)
private val ParagraphEndTag = Regex("</(p|h[1-6])\\s*>", RegexOption.IGNORE_CASE)
private val AnyTag = Regex("<[^>]+>")

/** One pass over `&name;`, `&#NN;` and `&#xHH;`, so a decoded `&` is never decoded again (`&amp;lt;` stays `&lt;`). */
private val Entity = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|nbsp|lt|gt|quot|apos|amp);")
private val HorizontalWhitespace = Regex("[ \\t\\u000B\\u000C\\u00A0]+")
private val ManyNewlines = Regex("\n{3,}")

/**
 * Turns a description's HTML into plain text for the page. Block ends and `<br>` become line breaks, so paragraphs
 * stay apart (unlike iOS, which joins them). Tags are removed before entities are decoded, so an escaped `&lt;b&gt;`
 * remains text.
 */
internal fun stripDescriptionHtml(raw: String): String {
    val withBreaks = raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(LineBreakTag, "\n")
        .replace(LineEndTag, "\n")
        .replace(ParagraphEndTag, "\n\n")
        .replace(AnyTag, "")
    return Entity.replace(withBreaks) { match -> decodeEntity(match.groupValues[1]) ?: match.value }
        .replace(HorizontalWhitespace, " ")
        .split('\n')
        .joinToString("\n") { it.trim() }
        .replace(ManyNewlines, "\n\n")
        .trim()
}

/** The text an entity [name] (without `&` and `;`) stands for, or null when a numeric one is not a code point. */
private fun decodeEntity(name: String): String? = when (name) {
    "nbsp" -> " "
    "lt" -> "<"
    "gt" -> ">"
    "quot" -> "\""
    "apos" -> "'"
    "amp" -> "&"
    else -> numericEntity(name)
}

private fun numericEntity(name: String): String? {
    val codePoint = if (name[1] == 'x' || name[1] == 'X') {
        name.drop(2).toIntOrNull(16)
    } else {
        name.drop(1).toIntOrNull()
    }
    return codePoint?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }
}

/**
 * The description to show for [raw], or null when there is nothing to show: no node is composed for a missing or
 * blank description, so the section takes no space.
 */
internal fun cleanDescription(raw: String?): String? = raw?.let(::stripDescriptionHtml)?.takeIf { it.isNotBlank() }
