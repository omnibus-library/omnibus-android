package com.omnibus.omnibus.data.audiobook.playback.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.omnibus.omnibus.data.audiobook.playback.NowPlaying
import com.omnibus.omnibus.data.audiobook.playback.NowPlayingImage
import com.omnibus.omnibus.data.audiobook.playback.RemotePressFilter
import com.omnibus.omnibus.data.audiobook.playback.RemoteTransport
import com.omnibus.omnibus.data.audiobook.playback.Span
import com.omnibus.omnibus.data.audiobook.playback.skipTarget
import com.omnibus.omnibus.data.audiobook.playback.spanAt
import com.omnibus.omnibus.data.audiobook.playback.toMillis

/**
 * The player the session wraps (R16, AND-STK-5). The system surfaces see the current chapter: duration and position are the
 * span's, seeks map back to book time, and the chapter title is the metadata. Skips are book time, not chapter time, and
 * the previous and next chapter commands are hidden. Its own listeners get the metadata and span changes that the system
 * seek bar needs.
 */
@OptIn(UnstableApi::class)
class ChapterForwardingPlayer internal constructor(
    exo: ExoPlayer,
    private val bridge: PlaybackServiceBridge,
    private val filter: RemotePressFilter,
    private val onPlayRequested: () -> Unit,
) : ForwardingPlayer(exo) {

    /** The session this player serves, set by the service right after it is built. */
    var session: MediaSession? = null

    private val syntheticListeners = mutableListOf<Player.Listener>()
    private var metadataFor: NowPlaying? = null
    private var metadata: MediaMetadata? = null

    init {
        bridge.addNowPlayingListener(::notifyNowPlayingChanged)
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        syntheticListeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        syntheticListeners -= listener
    }

    private fun bookDurationMs(): Long =
        super.getDuration().takeIf { it != C.TIME_UNSET && it > 0 }
            ?: (bridge.nowPlaying?.durationSeconds ?: 0.0).toMillis()

    private fun span(): Span? = bridge.nowPlaying?.let {
        spanAt(it.chapters, bookDurationMs(), super.getCurrentPosition())
    }

    override fun getDuration(): Long = span()?.lengthMs ?: super.getDuration()

    override fun getContentDuration(): Long = span()?.lengthMs ?: super.getContentDuration()

    override fun getCurrentPosition(): Long = span()?.toSpanMs(super.getCurrentPosition()) ?: super.getCurrentPosition()

    override fun getContentPosition(): Long = span()?.toSpanMs(super.getContentPosition()) ?: super.getContentPosition()

    override fun getBufferedPosition(): Long = span()?.toSpanMs(super.getBufferedPosition()) ?: super.getBufferedPosition()

    override fun getContentBufferedPosition(): Long =
        span()?.toSpanMs(super.getContentBufferedPosition()) ?: super.getContentBufferedPosition()

    override fun seekTo(positionMs: Long) {
        super.seekTo(span()?.toBookMs(positionMs) ?: positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        super.seekTo(mediaItemIndex, span()?.toBookMs(positionMs) ?: positionMs)
    }

    override fun seekBack() = remoteMove(RemoteTransport.SeekBackward)

    override fun seekForward() = remoteMove(RemoteTransport.SeekForward)

    override fun seekToPrevious() = remoteMove(RemoteTransport.PreviousTrack)

    override fun seekToPreviousMediaItem() = remoteMove(RemoteTransport.PreviousTrack)

    override fun seekToNext() = remoteMove(RemoteTransport.NextTrack)

    override fun seekToNextMediaItem() = remoteMove(RemoteTransport.NextTrack)

    /** A system skip in book time, clamped to the book, through the same-press filter. It never changes chapter. */
    internal fun remoteMove(transport: RemoteTransport) {
        if (super.getMediaItemCount() == 0 || !filter.shouldMove(transport)) return
        val durationSeconds = bookDurationMs() / 1000.0
        val target = skipTarget(super.getCurrentPosition() / 1000.0, durationSeconds, transport.seekDeltaSeconds)
        super.seekTo(target.toMillis())
    }

    override fun play() {
        if (super.getMediaItemCount() == 0 || super.getPlayerError() != null) {
            onPlayRequested()
        } else {
            super.play()
        }
    }

    override fun prepare() {
        if (super.getPlayerError() == null) super.prepare()
    }

    override fun pause() {
        if (requestIsExternal()) bridge.notifyExternalPause()
        super.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) {
            play()
            return
        }
        if (requestIsExternal()) bridge.notifyExternalPause()
        super.setPlayWhenReady(false)
    }

    /** True when the request came from a controller without the app's connection hint, such as a system control. */
    private fun requestIsExternal(): Boolean {
        val controller = session?.controllerForCurrentRequest ?: return false
        return !controller.connectionHints.getBoolean(AudiobookPlaybackService.HINT_APP_CONTROLLER, false)
    }

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().removeAll(*HIDDEN_COMMANDS).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command !in HIDDEN_COMMANDS && super.isCommandAvailable(command)

    override fun getMediaMetadata(): MediaMetadata {
        val np = bridge.nowPlaying ?: return super.getMediaMetadata()
        if (np == metadataFor) metadata?.let { return it }
        val chapterTitle = np.chapterIndex?.let { np.chapters.getOrNull(it) }?.title?.takeIf { it.isNotBlank() }
        val bookTitle = np.bookTitle.ifBlank { null }
        val artwork = when (val image = np.image) {
            is NowPlayingImage.Cover -> image.bytes.toByteArray()
            NowPlayingImage.Plate -> ArtworkPlate.render(np.bookUuid, np.bookTitle)
            NowPlayingImage.None -> null
        }
        return MediaMetadata.Builder()
            .setTitle(chapterTitle ?: bookTitle)
            .setDisplayTitle(chapterTitle ?: bookTitle)
            .setArtist(np.author)
            .setAlbumTitle(bookTitle)
            .apply { if (artwork != null) setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            .build()
            .also {
                metadataFor = np
                metadata = it
            }
    }

    /** Tells the session's listeners that the chapter, metadata or artwork changed, so the system seek bar updates. */
    private fun notifyNowPlayingChanged() {
        val current = mediaMetadata
        val events = Player.Events(
            FlagSet.Builder().addAll(Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_POSITION_DISCONTINUITY).build(),
        )
        syntheticListeners.toList().forEach {
            it.onMediaMetadataChanged(current)
            it.onEvents(this, events)
        }
    }

    override fun release() {
        bridge.removeNowPlayingListener(::notifyNowPlayingChanged)
        super.release()
    }

    private companion object {
        val HIDDEN_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        )
    }
}
