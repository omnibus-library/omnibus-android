package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.feature.reader.ui.ReaderScripts
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads the settled page and fills the snapshot ring for it: the page in front, then each neighbour the page can turn
 * to. Scheduling is the host's job; the cover is left up for the caller to drop.
 */
internal class CurlCapture(
    private val call: suspend (String) -> JsonElement?,
    private val snapshotter: PageSnapshotter,
    private val cover: (PageImage?) -> Unit,
) {
    var ring = CurlRing<PageImage>()
        private set

    var settled: SettledPage? = null
        private set

    val layout: CurlLayout? get() = CurlLayout.of(settled?.columns)

    /** Reads the settled page and, when [snapshots], fills what the ring is missing under a cover of the current page. */
    suspend fun run(snapshots: Boolean) {
        settled = SettledPage.decode(call(ReaderScripts.WHEN_SETTLED))
        if (!snapshots || layout == null) {
            clear()
            return
        }
        if (ring.current == null) {
            replaceRing(CurlRing(current = snapshotter.capture() ?: return))
        }
        cover(ring.current)
        var peeked = false
        for (dir in intArrayOf(1, -1)) {
            if (ring[dir] != null || settled?.kind(dir) != NeighbourKind.Page) continue
            if (call(ReaderScripts.peek(dir)) != JsonPrimitive(true)) continue
            peeked = true
            replaceRing(ring.with(dir, snapshotter.capture()))
        }
        if (peeked) call(ReaderScripts.peek(0))
    }

    /** Swaps in [next], recycling every image it no longer holds. */
    fun replaceRing(next: CurlRing<PageImage>) {
        ring.dropped(next).forEach { it.recycle() }
        ring = next
    }

    fun clear() = replaceRing(CurlRing())
}
