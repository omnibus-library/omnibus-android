package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.feature.reader.ui.ReaderScripts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.pow

/**
 * Keeps snapshots of the settled reader page and plays each tap-initiated turn as a curl over them, or lands it with
 * no motion when the mode or the environment asks for that. A port of iOS `PageCurlHost`. Main thread only.
 *
 * Its collaborators are parameters so the state machine runs on the JVM with fakes.
 */
internal class PageCurlHost(
    private val call: suspend (String) -> JsonElement?,
    private val evaluate: (String) -> Unit,
    private val snapshotter: PageSnapshotter,
    private val stage: CurlStage,
    private val clock: FrameClock,
    scope: CoroutineScope,
    private val environment: CurlEnvironment,
) {
    /** The host's own work, cancelled by [release] even when the scope it was given lives on. */
    private val work = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val capture = CurlCapture(call, snapshotter, stage::showCover)
    private val staleness = CurlStaleness()
    private val queue = TurnQueue()

    private var pageReady = false
    private var busy = false
    private var released = false
    private var selectionLive = false
    /** A repaint (not a relocate) arrived while a capture was running, so its result is stale on arrival. */
    private var changedWhileBusy = false
    private var cfi: String? = null
    /** The swipe directions the host claims, as last announced to the glue. */
    private var announced: Set<Int> = emptySet()
    /** The last `setCurlReady` sent as (on, next, prev), or null for the glue's default. */
    private var lastSent: Triple<Boolean, Boolean, Boolean>? = null
    /** The drag in progress, from [beginDrag] until [endDrag]. */
    private var dragging: DragSession? = null
    private var captureJob: Job? = null

    /** The page-turn mode. Changing it drops the snapshots; Slide makes no page calls at all. */
    var mode: ReaderPageTurn = ReaderPageTurn.Slide
        set(value) {
            if (value == field) return
            field = value
            capture.clear()
            queue.clear()
            staleness.markStale()
            if (value == ReaderPageTurn.Slide) announce() else pageChanged(byRelocate = false)
        }

    /** Whether a tap would curl right now: snapshots fresh and in place, and the turn is a curl. */
    val isReadyToCurl: Boolean
        get() = !busy && !staleness.stale && capture.ring.current != null && effective() == EffectiveTurn.Curl

    /** The page finished loading its first state, so it can be asked and snapshotted. */
    fun onPageReady() {
        pageReady = true
        pageChanged(byRelocate = false)
    }

    /** A new page replaced the old one under the reader; nothing it held is still valid. */
    fun onPageReplaced() {
        pageReady = false
        capture.clear()
        staleness.markStale()
        queue.clear()
        announced = emptySet()
        lastSent = null
    }

    fun onRelocate(cfi: String?) {
        this.cfi = cfi
        pageChanged(byRelocate = true)
    }

    fun onRepaint() = pageChanged(byRelocate = false)

    fun onSizeChanged() = pageChanged(byRelocate = false)

    fun onAnnotationTap() = pageChanged(byRelocate = false)

    fun onSelection() {
        selectionLive = true
        pageChanged(byRelocate = false)
    }

    fun onSelectionCleared() {
        selectionLive = false
        if (staleness.stale) pageChanged(byRelocate = false)
    }

    /** A gutter tap the glue handed over: 1 forward, -1 back. */
    fun tapTurn(dir: Int) {
        if (released) return
        if (busy) {
            queue.offer(dir)
            return
        }
        when (effective()) {
            EffectiveTurn.Slide -> evaluate(ReaderScripts.turnSlide(dir))
            EffectiveTurn.Instant -> instantTurn(dir)
            EffectiveTurn.Curl -> when {
                !canCurl() -> evaluate(ReaderScripts.turnSlide(dir))
                capture.ring[dir] != null -> startTurn { curlTap(dir) }
                capture.settled?.kind(dir) == NeighbourKind.Section -> startTurn { curlAcrossSection(dir) }
                else -> evaluate(ReaderScripts.turnSlide(dir))
            }
        }
    }

    /**
     * Decides what a touch that has moved ([dx], [dy]) from its down point becomes: a drag the host follows, a turn it
     * plays, or nothing. Only the directions announced to the glue can be claimed. See [CurlClaim.decide].
     */
    fun claimFor(dx: Float, dy: Float, slop: Float, elapsedMs: Long, pointers: Int): CurlClaim {
        if (released) return CurlClaim.Pass
        return CurlClaim.decide(
            dx, dy, slop, elapsedMs, pointers, selectionLive,
            announced = { it in announced },
            canDrag = { dir -> effective() == EffectiveTurn.Curl && canCurl() && capture.ring[dir] != null },
        )
    }

    /** Starts a drag in [dir] that follows the finger from its touch-down point at ([downX], [downY]). */
    fun beginDrag(dir: Int, downX: Float, downY: Float) {
        if (released || busy) return
        val current = capture.ring.current ?: return
        val destination = capture.ring[dir] ?: return
        val (turning, flap) = curlRects()
        val (front, beneath) = if (dir > 0) current to destination else destination to current
        val drag = CurlDrag(dir, turning, downX, downY)
        val session = DragSession(drag, flap, front, beneath, lastX = downX, fold = drag.fold(downX, downY))
        busy = true
        dragging = session
        stage.showCover(current)
        showFold(session, session.fold)
    }

    /** Moves the fold of the drag in progress to the finger at ([x], [y]). */
    fun dragTo(x: Float, y: Float) {
        val session = dragging ?: return
        session.lastX = x
        session.fold = session.drag.fold(x, y)
        showFold(session, session.fold)
    }

    /**
     * Ends the drag in progress at [velocityDp] (dp per second, negative leftwards). A release past halfway, or a flick
     * toward the turn, completes the turn; otherwise the page settles back.
     */
    fun endDrag(velocityDp: Float) {
        val session = dragging ?: return
        dragging = null
        val decision = CurlDrag.release(session.drag.dir, session.drag.progress(session.lastX), velocityDp)
        work.launch { settleDrag(session, decision) }
    }

    /** Stops all work and frees every snapshot. Nothing is evaluated on the page afterwards. */
    fun release() {
        if (released) return
        released = true
        work.cancel()
        dragging = null
        announced = emptySet()
        capture.clear()
        queue.clear()
        stage.showCurl(null)
        stage.showCover(null)
    }

    private fun effective(): EffectiveTurn =
        effectiveTurn(mode, environment.reducedMotion(), environment.isLowRam(), capture.layout)

    private fun canCurl(): Boolean =
        !busy && !staleness.stale && pageReady && !selectionLive && capture.ring.current != null

    /** The page or the settings changed: the snapshots are stale, and a capture is due after a short pause. */
    private fun pageChanged(byRelocate: Boolean) {
        if (released || mode == ReaderPageTurn.Slide) return
        if (!staleness.pageChanged(byRelocate, cfi, busy)) return
        if (busy) changedWhileBusy = true
        announce()
        if (busy) return
        capture.clear()
        captureJob?.cancel()
        captureJob = work.launch {
            delay(CAPTURE_DELAY_MS)
            runCapture()
        }
    }

    /** Snapshots what the ring is missing, under a cover of the page in front. */
    private suspend fun runCapture() {
        if (released || busy || !pageReady || selectionLive || mode == ReaderPageTurn.Slide) {
            if (!busy) stage.showCover(null)
            if (mode == ReaderPageTurn.Slide) capture.clear()
            return
        }
        busy = true
        changedWhileBusy = false
        val at = cfi
        capture.run(snapshots = mode == ReaderPageTurn.Curl && !environment.isLowRam() && !environment.reducedMotion())
        if (released) return
        staleness.captured(at, cfi)
        if (changedWhileBusy) staleness.markStale()
        busy = false
        stage.showCover(null)
        settle()
    }

    /** Catches up on a change that arrived during the capture, or runs a tap that queued behind it. */
    private fun settle() {
        announce()
        if (staleness.stale) {
            queue.clear()
            pageChanged(byRelocate = false)
        } else {
            queue.next()?.let(::tapTurn)
        }
    }

    /**
     * Tells the glue which swipes are the host's, so it leaves them alone: its gutter taps always are, and a swipe is
     * when the host claims that direction. Sent only when it changes.
     */
    private fun announce() {
        if (released) return
        val turn = if (pageReady) effective() else EffectiveTurn.Slide
        announced = setOf(1, -1).filterTo(mutableSetOf()) { claims(turn, it) }
        val sent = Triple(turn != EffectiveTurn.Slide, 1 in announced, -1 in announced)
        if (sent == (lastSent ?: GLUE_DEFAULT)) return
        lastSent = sent
        evaluate(ReaderScripts.setCurlReady(sent.first, sent.second, sent.third))
    }

    /**
     * Whether a swipe toward [dir] is the host's under [turn]: in Curl, a captured page it can follow or a section it
     * can turn; in None, any neighbour that exists.
     */
    private fun claims(turn: EffectiveTurn, dir: Int): Boolean {
        val kind = capture.settled?.kind(dir)
        return when (turn) {
            EffectiveTurn.Slide -> false
            EffectiveTurn.Curl -> !staleness.stale && (capture.ring[dir] != null || kind == NeighbourKind.Section)
            EffectiveTurn.Instant -> kind == NeighbourKind.Page || kind == NeighbourKind.Section
        }
    }

    /** Marks a turn as running before its coroutine starts, so a tap in the same frame queues rather than runs. */
    private fun startTurn(block: suspend () -> Unit) {
        busy = true
        work.launch { block() }
    }

    private fun instantTurn(dir: Int) {
        busy = true
        work.launch {
            call(ReaderScripts.turnInstant(dir))
            if (released) return@launch
            busy = false
            staleness.markStale()
            pageChanged(byRelocate = false)
        }
    }

    /** The timed curl to the snapshot in [dir], then the turn landed in the WebView. */
    private suspend fun curlTap(dir: Int) {
        val current = capture.ring.current
        val destination = capture.ring[dir]
        if (current == null || destination == null) {
            fail()
            return
        }
        stage.showCover(current)
        animate(dir, current, destination)
        if (released) return
        stage.showCover(destination)
        stage.showCurl(null)
        land(dir, destination = null)
    }

    /**
     * A turn across a chapter boundary: the next section is not laid out, so the turn lands under the cover, the
     * landed page is snapshotted, and the curl plays to it.
     */
    private suspend fun curlAcrossSection(dir: Int) {
        val current = capture.ring.current
        if (current == null) {
            fail()
            return
        }
        stage.showCover(current)
        if (call(ReaderScripts.turnInstant(dir)) != JsonPrimitive(true)) {
            fail()
            return
        }
        if (released) return
        snapshotter.awaitDrawn()
        val landed = snapshotter.capture()
        if (released) {
            landed?.recycle()
            return
        }
        if (landed == null) {
            fail()
            return
        }
        animate(dir, current, landed)
        if (released) {
            landed.recycle()
            return
        }
        stage.showCover(landed)
        stage.showCurl(null)
        land(dir, destination = landed)
    }

    /**
     * Keeps the cover up until the WebView has drawn the landed state, then drops it. A failed or stalled landing
     * marks the snapshots stale and recaptures.
     */
    private suspend fun land(dir: Int, destination: PageImage?) {
        val turned = destination != null || call(ReaderScripts.turnInstant(dir)) == JsonPrimitive(true)
        if (released) {
            destination?.recycle()
            return
        }
        val drawn = turned && snapshotter.awaitDrawn()
        if (released) {
            destination?.recycle()
            return
        }
        if (drawn) {
            capture.replaceRing(capture.ring.landed(dir, destination))
        } else {
            destination?.recycle()
            staleness.markStale()
        }
        busy = false
        if (staleness.stale) {
            stage.showCover(null)
            capture.clear()
            settle()
        } else {
            runCapture()
        }
    }

    /** Gives up on the turn: snapshots dropped, cover hidden, and a recapture scheduled. */
    private fun fail() {
        if (released) return
        staleness.markStale()
        busy = false
        stage.showCurl(null)
        stage.showCover(null)
        capture.clear()
        settle()
    }

    /**
     * Plays the curl from [from] to [to] over [CURL_MS], one frame at a time. A back turn runs the forward fold in
     * reverse, with the page turned to lying over the current one, as R7 asks.
     */
    private suspend fun animate(dir: Int, from: PageImage, to: PageImage) {
        val (turning, flap) = curlRects()
        val corner = Vec(turning.right, turning.top + CORNER_HEIGHT_FRACTION * turning.height)
        val (front, beneath) = if (dir > 0) from to to else to to from
        val start = clock.awaitFrame()
        while (true) {
            val now = clock.awaitFrame()
            if (released) return
            val t = ((now - start) / 1e6f / CURL_MS).coerceAtMost(1f)
            stage.showCurl(CurlPicture(CurlScene(turning, flap, corner, CurlDrag.tapFold(dir, turning, t)), front, beneath))
            if (t >= 1f) return
        }
    }

    /** The turning page and the flap bounds over the stage, from the settled layout and spine. */
    private fun curlRects(): Pair<CurlRect, CurlRect> {
        val bounds = CurlRect(0f, 0f, stage.widthPx.toFloat(), stage.heightPx.toFloat())
        val spine = capture.settled?.spine?.let { it * stage.density }
        val layout = capture.layout ?: CurlLayout.Single
        return layout.turning(bounds, spine) to layout.flapBounds(bounds, spine)
    }

    private fun showFold(session: DragSession, fold: Vec) {
        val scene = CurlScene(session.drag.turning, session.flap, session.drag.corner, fold)
        stage.showCurl(CurlPicture(scene, session.front, session.beneath))
    }

    /**
     * Eases the fold from the finger's last point to where the release sends it, then completes the turn under the
     * cover, or hides the curl and settles back.
     */
    private suspend fun settleDrag(session: DragSession, decision: CurlDecision) {
        val from = session.fold
        val to = session.drag.target(decision)
        val start = clock.awaitFrame()
        while (true) {
            val now = clock.awaitFrame()
            if (released) return
            val t = ((now - start) / 1e6f / DRAG_SETTLE_MS).coerceAtMost(1f)
            val eased = 1 - (1 - t).pow(3)
            showFold(session, Vec(from.x + (to.x - from.x) * eased, from.y + (to.y - from.y) * eased))
            if (t >= 1f) break
        }
        if (decision == CurlDecision.Commit) {
            val destination = capture.ring[session.drag.dir]
            if (destination == null) {
                fail()
                return
            }
            stage.showCover(destination)
            stage.showCurl(null)
            land(session.drag.dir, destination = null)
        } else {
            stage.showCurl(null)
            stage.showCover(null)
            busy = false
            settle()
        }
    }

    private companion object {
        /** Taps that arrive within this of a change wait for the page to settle before the snapshots are taken. */
        const val CAPTURE_DELAY_MS = 120L

        /** How long a gutter tap's curl takes, in milliseconds. */
        const val CURL_MS = 650f

        /** How long a released drag takes to settle or complete, in milliseconds. */
        const val DRAG_SETTLE_MS = 250f

        /** Where the lifted corner sits down the turning page's right edge, as a fraction of its height. */
        const val CORNER_HEIGHT_FRACTION = 0.7f

        /** The glue's state before anything is announced: curl off, no swipes claimed. */
        val GLUE_DEFAULT = Triple(false, false, false)
    }
}

/** A drag in progress: its fold geometry, the two pages it moves between, and the finger's last point and fold. */
private class DragSession(
    val drag: CurlDrag,
    val flap: CurlRect,
    val front: PageImage,
    val beneath: PageImage,
    var lastX: Float,
    var fold: Vec,
)
