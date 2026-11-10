package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.data.reader.ReaderPageTurn
import kotlin.math.abs
import kotlin.math.sign

/** Whether a touch is taken by the page turn host, and how. */
internal sealed interface CurlClaim {
    /** Not taken: the WebView keeps the touch. */
    data object Pass : CurlClaim

    /** Taken to follow the finger in direction [dir]. */
    data class Drag(val dir: Int) : CurlClaim

    /** Taken as an announced turn in direction [dir] that can't be dragged now, played as a tap turn. */
    data class Turn(val dir: Int) : CurlClaim

    companion object {
        /** The glue's long-press delay (`epub-reader-glue.js` `pressTimer`, 420 ms). */
        const val LONG_PRESS_MS = 420L

        /**
         * Decides a touch that has moved ([dx], [dy]) from its down point after [elapsedMs]. A horizontal move past
         * [slop] with one pointer, short of a long press and with no live selection, is claimed in its direction when
         * the host has announced that direction. A draggable direction is followed, and one that is not is turned if
         * the move is more horizontal than vertical.
         */
        fun decide(
            dx: Float,
            dy: Float,
            slop: Float,
            elapsedMs: Long,
            pointers: Int,
            selectionLive: Boolean,
            announced: (Int) -> Boolean,
            canDrag: (Int) -> Boolean,
        ): CurlClaim {
            if (pointers != 1 || abs(dx) < slop || elapsedMs >= LONG_PRESS_MS || selectionLive) return Pass
            val dir = if (dx < 0) 1 else -1
            if (!announced(dir)) return Pass
            if (canDrag(dir)) return Drag(dir)
            return if (abs(dx) > abs(dy)) Turn(dir) else Pass
        }
    }
}

/** Taps that arrive during a turn, signed by direction and at most two ahead (iOS `queuedTurns`). */
internal class TurnQueue {
    private var count = 0

    /** Queues a turn in [dir]. A turn in the opposite direction replaces the queue. */
    fun offer(dir: Int) {
        count = if (count.sign == dir) (count + dir).coerceIn(-2, 2) else dir
    }

    /** Takes the next queued direction, or null when nothing is queued. */
    fun next(): Int? = count.sign.takeIf { it != 0 }?.also { count -= it }

    /** Drops everything queued. */
    fun clear() {
        count = 0
    }
}

/** The turn animation a page turn plays. */
internal enum class EffectiveTurn { Slide, Curl, Instant }

/**
 * The turn that plays for [mode]. A curl needs a layout the host can draw and a device that is not low on RAM, and
 * with reduced motion it lands at once instead.
 */
internal fun effectiveTurn(mode: ReaderPageTurn, reducedMotion: Boolean, lowRam: Boolean, layout: CurlLayout?): EffectiveTurn =
    when (mode) {
        ReaderPageTurn.Slide -> EffectiveTurn.Slide
        ReaderPageTurn.None -> EffectiveTurn.Instant
        ReaderPageTurn.Curl -> when {
            lowRam || layout == null -> EffectiveTurn.Slide
            reducedMotion -> EffectiveTurn.Instant
            else -> EffectiveTurn.Curl
        }
    }
