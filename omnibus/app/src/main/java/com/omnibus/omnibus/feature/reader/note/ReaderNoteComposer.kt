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
 * @property existing The note being edited; non-null means edit mode. Never blank when set.
 * @property confirmingDelete Whether the "Delete this note?" dialog is up.
 * @property deleting Whether a delete is in flight. Kept apart from [saving] so Save does not spin for a delete.
 * @property deleteFailed Whether the last delete failed. [failed] stays the save's flag.
 */
data class NoteComposerState(
    val cfiRange: String,
    val quote: String?,
    val text: String = "",
    val saving: Boolean = false,
    val failed: Boolean = false,
    val confirmingDiscard: Boolean = false,
    val existing: String? = null,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    val deleteFailed: Boolean = false,
) {
    /** Whether the screen edits an existing note. */
    val isEditing: Boolean get() = existing != null

    /** Whether a save or a delete is in flight. */
    internal val busy: Boolean get() = saving || deleting

    /** Whether leaving would lose something: any text for a new note, a trimmed change for an existing one. */
    val isChanged: Boolean get() = if (existing == null) text.isNotBlank() else text.trim() != existing.trim()

    /** Whether Save is on: some non-blank text that changes the note, and no write in flight. */
    val canSave: Boolean get() = text.isNotBlank() && isChanged && !busy

    /** Whether the trash button is on: an existing note, and no write in flight. */
    val canDelete: Boolean get() = isEditing && !busy
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
