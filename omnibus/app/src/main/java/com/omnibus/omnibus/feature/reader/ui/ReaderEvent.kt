package com.omnibus.omnibus.feature.reader.ui

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

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

    /** A gutter tap the glue handed to the host while it curls: 1 forward, -1 back. */
    data class TurnRequest(val dir: Int) : ReaderEvent

    /** The answer to a host page call; [value] is null when the call failed or answered nothing. */
    data class HostReply(val id: Int, val value: JsonElement?) : ReaderEvent

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
                "turnRequest" -> message.payload?.toIntOrNull()?.takeIf { it == 1 || it == -1 }?.let(::TurnRequest)
                "hostReply" -> message.payload?.let { ReaderJson.decodeFromString<HostReplyData>(it) }?.let {
                    HostReply(it.id, it.value?.takeUnless { value -> value is JsonNull })
                }
                else -> null
            }
        } catch (e: IllegalArgumentException) {
            // SerializationException is a subtype: a truncated, wrong-shaped or wrong-typed payload.
            null
        }
    }
}
