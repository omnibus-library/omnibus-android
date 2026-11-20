package com.omnibus.omnibus.feature.reader.note

import com.omnibus.omnibus.data.highlights.HIGHLIGHT_NOTE_MAX_LENGTH

/**
 * The note screen's state, kept in the reader's ViewModel so it survives rotation.
 *
 * @property cfiRange The trimmed range of the highlight the note is for.
 * @property quote The highlight's text, shown above the field; null or blank shows none.
 * @property text What the reader has typed, at most [HIGHLIGHT_NOTE_MAX_LENGTH] code points.
 * @property saving Whether a save is in flight. Save, close and typing wait for it.
 * @property failed Whether the last save failed, which shows the error.
 * @property confirmingDiscard Whether the "Discard note?" dialog is up.
 */
data class NoteComposerState(
    val cfiRange: String,
    val quote: String?,
    val text: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
    val confirmingDiscard: Boolean = false,
) {
    /** Whether Save is on: some non-blank text, and no save in flight. */
    val canSave: Boolean get() = text.isNotBlank() && !saving
}

/** The length at which the counter appears, in code points. */
internal const val NOTE_COUNTER_FROM = 3900

/** [text]'s length in code points, as the server counts it. */
internal fun noteLength(text: String): Int = text.codePointCount(0, text.length)

/** Whether the counter shows under the field. */
internal val NoteComposerState.showsCounter: Boolean get() = noteLength(text) >= NOTE_COUNTER_FROM

/** [text] cut to [HIGHLIGHT_NOTE_MAX_LENGTH] code points, never inside a surrogate pair. */
internal fun cappedNote(text: String): String =
    if (noteLength(text) <= HIGHLIGHT_NOTE_MAX_LENGTH) text
    else text.substring(0, text.offsetByCodePoints(0, HIGHLIGHT_NOTE_MAX_LENGTH))
