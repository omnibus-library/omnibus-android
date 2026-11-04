package com.omnibus.omnibus.data.audiobook.playback.media3

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.RemotePressFilter
import com.omnibus.omnibus.di.TimeSource
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The media session service (R15). It owns one ExoPlayer and one [MediaSession], wrapped in a [ChapterForwardingPlayer], so
 * the notification, the lock screen and Bluetooth all see the current chapter. Media3 manages the foreground state from
 * the player's state.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudiobookPlaybackService : MediaSessionService() {

    @Inject lateinit var playerFactory: AudiobookPlayerFactory
    @Inject lateinit var bridge: PlaybackServiceBridge

    /** Lazy, because the controller connects to this service. */
    @Inject lateinit var controller: Lazy<AudiobookController>

    @Inject lateinit var timeSource: TimeSource

    private var player: ChapterForwardingPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate() // Hilt injects here.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.audiobook_playback_notification_channel)
                .build(),
        )
        val exo = playerFactory.createPlayer()
        val forwarding = ChapterForwardingPlayer(exo, bridge, RemotePressFilter(timeSource)) {
            controller.get().play()
        }
        val builder = MediaSession.Builder(this, forwarding)
            .setCallback(AudiobookSessionCallback(forwarding))
            .setMediaButtonPreferences(AudiobookSessionCallback.buttons(this))
            .setBitmapLoader(
                CacheBitmapLoader(
                    DataSourceBitmapLoader.Builder(this).setMaximumOutputDimension(MAX_ARTWORK_PX).build(),
                ),
            )
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        val built = builder.build()
        forwarding.session = built
        player = forwarding
        session = built
        bridge.mediaSession = built
        bridge.attach(exo)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        bridge.detach()
        bridge.mediaSession = null
        session?.release()
        player?.release()
        session = null
        player = null
        super.onDestroy()
    }

    companion object {
        /** The connection hint the app's own controller sends, so its commands aren't taken for system ones. */
        const val HINT_APP_CONTROLLER = "com.omnibus.omnibus.audiobook.APP_CONTROLLER"

        /** The largest artwork the session loads, in pixels on either side. */
        const val MAX_ARTWORK_PX = 1024
    }
}
