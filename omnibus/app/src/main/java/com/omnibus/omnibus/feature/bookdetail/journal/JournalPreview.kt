package com.omnibus.omnibus.feature.bookdetail.journal

/**
 * One piece of a journal preview: text the reader sees, or filler standing in for a spoiler. The real spoiler text is in
 * no run, so it cannot reach the screen or the accessibility tree.
 */
sealed interface PreviewRun {

    /** Visible text, with its Markdown flattened. */
    data class Text(val text: String) : PreviewRun

    /** A spoiler's stand-in: lorem-like filler the same length as the hidden text. */
    data class Veiled(val filler: String) : PreviewRun
}

/** The words a spoiler's filler is cut from. Its length is 55, so a filler cycles through it. */
internal const val JOURNAL_FILLER = "lorem ipsum dolor sit amet consectetur adipiscing elit "

/** The first and last private-use characters. Typed ones are dropped, so they can never stand in for a spoiler. */
private const val PUA_FIRST = '\uE000'
private const val PUA_LAST = '\uF8FF'

/** An escaped ASCII character is held as `ESCAPE_BASE + its code` while the line is flattened. */
private const val ESCAPE_BASE = 0xE000

/** Spoiler span `k` is held as `VEIL_BASE + k` while the line is flattened. */
private const val VEIL_BASE = 0xE100
private const val VEIL_LAST = 0xF7FF

/** A `||spoiler||` pair, matched left to right within a line. */
private val SPOILER = Regex("""\|\|(.*?)\|\|""")

/** The one block marker a line may start with: a bullet, a number, a heading or a quote. */
private val BLOCK_MARKER = Regex("""^\s*([-*+]\s+|\d{1,9}[.)]\s+|#{1,6}\s+|>\s?)""")

/** An inline code span, whose text is kept verbatim. */
private val CODE_SPAN = Regex("""`([^`]*)`""")

/** A backslash escape of an ASCII punctuation character. */
private val ESCAPE = Regex("""\\([!-/:-@\[-`{-~])""")

/** An image, which is removed. */
private val IMAGE = Regex("""\s*!\[[^\]]*]\([^)]*\)""")

/** An inline link, which keeps its text. */
private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")

/** An autolink, which shows its URL. */
private val AUTOLINK = Regex("""<(https?://[^>\s]+)>""")

/** Emphasis markers around a run of text, each kept only when they wrap a word. */
private val BOLD_STARS = Regex("""\*\*(?=\S)(.+?)(?<=\S)\*\*""")
private val BOLD_UNDERSCORES = Regex("""(?<![\w_])__(?=\S)(.+?)(?<=\S)__(?![\w_])""")
private val STRIKE = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
private val STAR_WRAP = Regex("""(?<![\w*])\*(?=\S)(.+?)(?<=\S)\*(?![\w*])""")
private val UNDERSCORE_WRAP = Regex("""(?<![\w_])_(?=\S)(.+?)(?<=\S)_(?![\w_])""")

/**
 * The preview of an entry's Markdown body: its first non-blank line, flattened to plain text, with each spoiler as a
 * [PreviewRun.Veiled] run. An empty or blank body gives no runs.
 */
internal fun journalPreview(bodyMd: String): List<PreviewRun> {
    val line = bodyMd.replace("\r\n", "\n").replace('\r', '\n')
        .split('\n')
        .firstOrNull { it.isNotBlank() }
        ?.filterNot { it in PUA_FIRST..PUA_LAST }
        ?: return emptyList()

    val fillers = mutableListOf<String>()
    val veiled = SPOILER.replace(line) { match ->
        val length = flattenInline(match.groupValues[1]).length
        if (length == 0 || VEIL_BASE + fillers.size > VEIL_LAST) {
            ""
        } else {
            fillers += journalFiller(length)
            (VEIL_BASE + fillers.size - 1).toChar().toString()
        }
    }

    val flat = flattenInline(veiled.replaceFirst(BLOCK_MARKER, "")).trim()

    val runs = mutableListOf<PreviewRun>()
    val text = StringBuilder()
    for (c in flat) {
        if (c.code in VEIL_BASE..VEIL_LAST) {
            if (text.isNotEmpty()) runs += PreviewRun.Text(text.toString()).also { text.clear() }
            runs += PreviewRun.Veiled(fillers[c.code - VEIL_BASE])
        } else {
            text.append(c)
        }
    }
    if (text.isNotEmpty()) runs += PreviewRun.Text(text.toString())
    return runs
}

/**
 * The text a screen reader speaks for [runs]: each veiled run is [spoilerHidden], so the filler is never read.
 */
internal fun spokenPreview(runs: List<PreviewRun>, spoilerHidden: String): String = runs.joinToString("") { run ->
    when (run) {
        is PreviewRun.Text -> run.text
        is PreviewRun.Veiled -> spoilerHidden
    }
}

/** Filler of exactly [length] characters, cut from [JOURNAL_FILLER] and repeated as needed. */
internal fun journalFiller(length: Int): String =
    buildString(length) { repeat(length) { append(JOURNAL_FILLER[it % JOURNAL_FILLER.length]) } }

/** Flattens inline Markdown, leaving code spans verbatim. */
private fun flattenInline(text: String): String {
    val out = StringBuilder()
    var last = 0
    for (code in CODE_SPAN.findAll(text)) {
        out.append(flattenProse(text.substring(last, code.range.first)))
        out.append(code.groupValues[1])
        last = code.range.last + 1
    }
    out.append(flattenProse(text.substring(last)))
    return out.toString()
}

/**
 * Flattens the inline Markdown outside code spans. Escapes are held as private-use characters until the end, so an
 * escaped `*` cannot start emphasis.
 */
private fun flattenProse(text: String): String =
    text.replace(ESCAPE) { (ESCAPE_BASE + it.groupValues[1][0].code).toChar().toString() }
        .replace(IMAGE, "")
        .replace(LINK, "$1")
        .replace(AUTOLINK, "$1")
        .replace(BOLD_STARS, "$1")
        .replace(BOLD_UNDERSCORES, "$1")
        .replace(STRIKE, "$1")
        .replace(STAR_WRAP, "$1")
        .replace(UNDERSCORE_WRAP, "$1")
        .map { if (it.code in ESCAPE_BASE until VEIL_BASE) (it.code - ESCAPE_BASE).toChar() else it }
        .joinToString("")
