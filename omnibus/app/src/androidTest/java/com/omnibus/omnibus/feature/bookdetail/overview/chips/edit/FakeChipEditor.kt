package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.VocabularyEntry

/** A [ChipEditUIState] that is bound, signed in, and with the given lists and sheet state. */
internal fun editorState(
    genres: List<String> = emptyList(),
    tags: List<String> = emptyList(),
    isOnline: Boolean = true,
    openKind: MetadataListKind? = null,
    entry: String = "",
    pools: Map<MetadataListKind, PoolState> = emptyMap(),
    failure: ChipEditFailure? = null,
    bookUpdate: ChipEditBookUpdate? = null,
) = ChipEditUIState(
    canEdit = true,
    isOnline = isOnline,
    bound = true,
    genres = genres,
    tags = tags,
    openKind = openKind,
    entry = entry,
    pools = pools,
    failure = failure,
    bookUpdate = bookUpdate,
)

/** A pool that is loaded with [names], each with a book count of one. */
internal fun loadedPool(vararg names: String): PoolState.Loaded =
    PoolState.Loaded(names.map { VocabularyEntry(it, 1) })

/**
 * A [ChipEditor] for instrumented tests. It records each call as text, such as `open:Genres` or `add:Genres:Noir`.
 * With [applyEdits] it also applies opening, closing, adding and removing to its state, as the ViewModel would.
 */
internal class FakeChipEditor(
    initial: ChipEditUIState = editorState(),
    private val applyEdits: Boolean = false,
) : ChipEditor {

    var current by mutableStateOf(initial)

    override val state: ChipEditUIState get() = current

    /** Every call, in order. */
    val calls = mutableListOf<String>()

    override fun bind(book: Book) {
        calls += "bind"
    }

    override fun open(kind: MetadataListKind) {
        calls += "open:$kind"
        if (applyEdits) current = current.copy(openKind = kind)
    }

    override fun close() {
        calls += "close"
        current = current.copy(openKind = null)
    }

    override fun add(kind: MetadataListKind, value: String) {
        calls += "add:$kind:$value"
        if (applyEdits) current = current.withValues(kind, current.valuesOf(kind) + value)
    }

    override fun remove(kind: MetadataListKind, value: String) {
        calls += "remove:$kind:$value"
        if (applyEdits) current = current.withValues(kind, current.valuesOf(kind).filterNot { it == value })
    }

    override fun updateEntry(text: String) {
        current = current.copy(entry = text)
    }

    override fun failureShown(eventId: Long) {
        calls += "failureShown:$eventId"
        if (current.failure?.eventId == eventId) current = current.copy(failure = null)
    }

    override fun bookUpdateApplied(eventId: Long) {
        calls += "bookUpdateApplied:$eventId"
        if (current.bookUpdate?.eventId == eventId) current = current.copy(bookUpdate = null)
    }
}
