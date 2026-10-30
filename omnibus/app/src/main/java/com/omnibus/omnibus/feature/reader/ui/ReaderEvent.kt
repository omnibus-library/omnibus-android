package com.omnibus.omnibus.feature.reader.ui

/**
 * Something the reader page reported, or the host reports on its behalf. [decode] turns the page's
 * raw [ReaderMessage] into one of these, so the rest of the reader never parses envelopes.
 */
sealed interface ReaderEvent {

    /** The page loaded and is ready to be booted. */
    data object HostReady : ReaderEvent

    /** The page's own status, such as `ready` or `error`. Load status comes from [LoadStatusChanged]. */
    data class Status(val state: String) : ReaderEvent

    /** The reader moved, or the page re-settled after a layout change. */
    data class Relocate(val data: RelocateData) : ReaderEvent

    /** The table of contents. The page sends it twice: bare first, then with page and percent. */
    data class Toc(val entries: List<TocEntry>) : ReaderEvent

    /** A text selection is being made or has changed. */
    data class Selection(val data: SelectionData) : ReaderEvent

    /** The selection was cleared. */
    data object SelectionCleared : ReaderEvent

    /** The reader tapped an existing annotation. */
    data class AnnotationTap(val data: AnnotationTapData) : ReaderEvent

    /** The centre tap, which shows or hides the reading chrome. */
    data object ToggleChrome : ReaderEvent

    /** The reader asked to share selected text. */
    data class ShareText(val text: String) : ReaderEvent

    /** The reader asked to share a quote card image. */
    data class ShareImage(val data: ShareImageData) : ReaderEvent

    /** A page turn the glue asked for, `next` or `previous`. */
    data class TurnRequest(val direction: String) : ReaderEvent

    /**
     * The host's load status, emitted whenever the controller's status changes, including the
     * initial `Loading`. The page never sends this.
     */
    data class LoadStatusChanged(val status: ReaderLoadStatus) : ReaderEvent

    companion object {
        /**
         * Decodes a page message, or returns null for an unknown type or a payload that doesn't
         * decode. Never throws.
         */
        fun decode(message: ReaderMessage): ReaderEvent? = try {
            when (message.type) {
                "hostReady" -> HostReady
                "status" -> message.payload?.let(::Status)
                "relocate" -> message.payload?.let { Relocate(ReaderJson.decodeFromString<RelocateData>(it)) }
                "toc" -> message.payload?.let { Toc(ReaderJson.decodeFromString<List<TocEntry>>(it)) }
                "selection" -> message.payload?.let { Selection(ReaderJson.decodeFromString<SelectionData>(it)) }
                "selectionCleared" -> SelectionCleared
                "annotationTap" -> message.payload?.let {
                    AnnotationTap(ReaderJson.decodeFromString<AnnotationTapData>(it))
                }
                "toggleChrome" -> ToggleChrome
                "shareText" -> message.payload?.let(::ShareText)
                "shareImage" -> message.payload?.let { ShareImage(ReaderJson.decodeFromString<ShareImageData>(it)) }
                "turnRequest" -> message.payload?.let(::TurnRequest)
                else -> null
            }
        } catch (e: IllegalArgumentException) {
            // SerializationException is a subtype: a truncated, wrong-shaped or wrong-typed payload.
            null
        }
    }
}
