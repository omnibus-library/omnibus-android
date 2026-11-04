package com.omnibus.omnibus.data.audiobook.playback.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.omnibus.omnibus.data.audiobook.playback.AudioSource
import com.omnibus.omnibus.data.audiobook.playback.audioCallFactory
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the ExoPlayer the service owns and the sources it plays (R15). Audio fetches go through the app's OkHttp client
 * with the source's headers (AND-STK-3), so they share its interceptors.
 */
@OptIn(UnstableApi::class)
@Singleton
class AudiobookPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val omnibusHttpClient: OmnibusHttpClient,
) {

    /** An ExoPlayer for speech that holds audio focus, pauses on becoming noisy and keeps the network awake. */
    fun createPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    /** The media source for [source], fetched over the app's HTTP client. */
    fun mediaSource(source: AudioSource): MediaSource = mediaSource(
        source,
        OkHttpDataSource.Factory(audioCallFactory(omnibusHttpClient)).setDefaultRequestProperties(source.headers),
    )

    /**
     * The media source for [source] over [dataSourceFactory]. A `direct` book is one concatenation of its parts, each with
     * its placeholder length, and an `hls` book is one playlist. The part mime is not set, so the server's value can't
     * steer extractor choice.
     */
    internal fun mediaSource(source: AudioSource, dataSourceFactory: DataSource.Factory): MediaSource = when (source) {
        is AudioSource.Parts -> ConcatenatingMediaSource2.Builder()
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setMediaItem(MediaItem.Builder().setMediaId(source.mediaId).build())
            .apply { source.parts.forEach { add(MediaItem.fromUri(it.url), it.placeholderMillis) } }
            .build()
        is AudioSource.Hls -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(
            MediaItem.Builder()
                .setMediaId(source.mediaId)
                .setUri(source.url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build(),
        )
    }
}
