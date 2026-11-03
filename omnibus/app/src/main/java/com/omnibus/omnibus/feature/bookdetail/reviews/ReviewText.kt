package com.omnibus.omnibus.feature.bookdetail.reviews

/*
 * Reviews are Markdown on the server, and the app shows them as plain text. The rules are line-based and conservative:
 * block markers go first on each line, then inline markers, so `* b` is a bullet and not emphasis.
 */

private val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val HEADING = Regex("""^\s{0,3}#{1,6}\s+""")
private val QUOTE = Regex("""^\s*>\s?""")
private val BULLET = Regex("""^\s*[-*+]\s+""")
private val INLINE_SPOILER = Regex("""\|\|(.+?)\|\|""")
private val STAR_WRAP = Regex("""(?<![\w*])\*(?=\S)(.+?)(?<=\S)\*(?![\w*])""")
private val UNDERSCORE_WRAP = Regex("""(?<![\w_])_(?=\S)(.+?)(?<=\S)_(?![\w_])""")
private val BLANK_RUN = Regex("""\n{3,}""")

/**
 * A review's Markdown as readable plain text. Images are dropped, links keep their text, headings, quotes and list
 * markers become plain lines, and emphasis marks are removed where they wrap a word. An inline `||spoiler||` reads as
 * [inlineSpoiler] unless the book is [finished]. Null when nothing readable is left.
 */
internal fun reviewPlainText(md: String?, finished: Boolean, inlineSpoiler: String): String? {
    if (md.isNullOrBlank()) return null
    val text = md.replace("\r\n", "\n").replace('\r', '\n').lines().joinToString("\n") { line ->
        line.replace(IMAGE, "").replace(LINK, "\$1").replace(HEADING, "").replace(QUOTE, "").replace(BULLET, "• ")
    }
        .replace(INLINE_SPOILER) { if (finished) it.groupValues[1] else inlineSpoiler }
        .replace("**", "").replace("__", "").replace("~~", "").replace("`", "")
        .replace(STAR_WRAP, "\$1").replace(UNDERSCORE_WRAP, "\$1")
        .replace(BLANK_RUN, "\n\n")
        .trim()
    return text.ifBlank { null }
}
