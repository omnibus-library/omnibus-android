package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.di.TimeSource

/**
 * The remote commands that skip, as a port of iOS `RemoteTransport`. Android acts on the first key down, so there is no
 * hold tracking here.
 */
internal enum class RemoteTransport(val seekDeltaSeconds: Double) {
    SkipForward(SKIP_FORWARD_SECONDS),
    SkipBackward(-SKIP_BACK_SECONDS),
    NextTrack(SKIP_FORWARD_SECONDS),
    PreviousTrack(-SKIP_BACK_SECONDS),
    SeekForward(SKIP_FORWARD_SECONDS),
    SeekBackward(-SKIP_BACK_SECONDS);

    /** True for the commands that move forward. */
    val isForward: Boolean get() = seekDeltaSeconds > 0

    /**
     * True when [previous] and this command are one press, which the filter moves once (I-SYS-3). That is a different
     * same-direction command within the window. A negative elapsed time counts too, as iOS does.
     */
    fun isSamePress(previous: RemoteTransport, elapsedMillis: Long): Boolean =
        this != previous && isForward == previous.isForward && elapsedMillis < SAME_PRESS_WINDOW_MILLIS

    companion object {
        /** The window in which a different same-direction command is the same press. Exclusive at 1 s, as iOS. */
        const val SAME_PRESS_WINDOW_MILLIS = 1_000L

        // Values of android.view.KeyEvent's constants, so this file stays JVM-only. The A/ test checks they match.
        const val KEYCODE_MEDIA_NEXT = 87
        const val KEYCODE_MEDIA_PREVIOUS = 88
        const val KEYCODE_MEDIA_REWIND = 89
        const val KEYCODE_MEDIA_FAST_FORWARD = 90
        const val KEYCODE_MEDIA_SKIP_FORWARD = 272
        const val KEYCODE_MEDIA_SKIP_BACKWARD = 273
        const val ACTION_DOWN = 0

        /** The command a media key sends, or null for keys that aren't skips (play and pause stay with Media3). */
        fun forMediaKey(keyCode: Int): RemoteTransport? = when (keyCode) {
            KEYCODE_MEDIA_NEXT -> NextTrack
            KEYCODE_MEDIA_PREVIOUS -> PreviousTrack
            KEYCODE_MEDIA_REWIND -> SeekBackward
            KEYCODE_MEDIA_FAST_FORWARD -> SeekForward
            KEYCODE_MEDIA_SKIP_FORWARD -> SkipForward
            KEYCODE_MEDIA_SKIP_BACKWARD -> SkipBackward
            else -> null
        }

        /** True for the first key down of a press, and not for its key up or auto-repeats. */
        fun actsOn(action: Int, repeatCount: Int): Boolean = action == ACTION_DOWN && repeatCount == 0
    }
}

/**
 * Decides whether a skip command moves the position, so two different same-direction commands that arrive within a
 * second, such as a headset and a lock screen for one press, move it once (I-SYS-3).
 */
internal class RemotePressFilter(private val timeSource: TimeSource) {

    private var last: RemoteTransport? = null
    private var lastAtMillis = 0L

    /** True when [transport] should move the position now. */
    fun shouldMove(transport: RemoteTransport): Boolean {
        val now = timeSource.nowMillis()
        val previous = last
        if (previous != null && transport.isSamePress(previous, now - lastAtMillis)) {
            // A coalesced pair is two commands, so a third is another press.
            last = null
            return false
        }
        last = transport
        lastAtMillis = now
        return true
    }
}
