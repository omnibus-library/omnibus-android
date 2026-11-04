package com.omnibus.omnibus.data.audiobook.playback.media3

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.Chapter
import com.omnibus.omnibus.data.audiobook.playback.NowPlaying
import com.omnibus.omnibus.data.audiobook.playback.NowPlayingImage
import android.media.MediaMetadata as PlatformMetadata
import android.media.session.MediaController as PlatformController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The media session service on the device (R2, R15–R17). Each test connects its own controller with the app's hint,
 * which starts the service, and releases it at the end. The fixture book is 130 seconds in three parts, with chapters at
 * 0, 60 and 110 seconds.
 */
@OptIn(UnstableApi::class)
class AudiobookPlaybackServiceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bridge by lazy { entryPoint(context).playbackServiceBridge() }
    private val factory by lazy { entryPoint(context).audiobookPlayerFactory() }
    private val controllers = mutableListOf<MediaController>()

    @After
    fun tearDown() {
        onMain {
            controllers.forEach { it.release() }
            bridge.nowPlaying = null
        }
        controllers.clear()
        context.stopService(Intent(context, AudiobookPlaybackService::class.java))
    }

    private fun connect(): MediaController =
        connectController(context, appController = true).also { controllers += it }

    /** Loads the fixture into the service's player, shows chapter two, and seeks to [positionMs]. */
    private fun startBookAt(positionMs: Long): ExoPlayer {
        awaitUntil("the service's player") { bridge.player != null }
        val exo = onMain { bridge.player!! }
        prepareLocalBook(context, factory, exo)
        onMain { bridge.nowPlaying = nowPlaying(chapterIndex = 1) }
        onMain { exo.seekTo(positionMs) }
        awaitUntil("a seek to $positionMs") { near(positionMs, exo.currentPosition) }
        return exo
    }

    private fun nowPlaying(chapterIndex: Int?) = NowPlaying(
        bookUuid = "book-1",
        bookTitle = "The Book",
        author = "An Author",
        chapters = CHAPTERS,
        durationSeconds = 130.0,
        chapterIndex = chapterIndex,
        image = NowPlayingImage.None,
    )

    @Test
    fun `declares the service and permissions (AND-STK-1, AND-STK-2, P-PLAY-1)`() {
        // TC-91: the merged manifest exports the service with the media playback type, requests the two foreground
        // permissions, doesn't ask for notifications, and resolves the Media3 session action.
        val packageManager = context.packageManager
        val info = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS,
        )
        val service = info.services.orEmpty().first { it.name == AudiobookPlaybackService::class.java.name }
        assertTrue(service.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue(service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK != 0)
        }

        val permissions = info.requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertFalse(permissions.contains("android.permission.POST_NOTIFICATIONS"))

        val resolved = packageManager.queryIntentServices(
            Intent("androidx.media3.session.MediaSessionService").setPackage(context.packageName),
            0,
        )
        assertTrue(resolved.any { it.serviceInfo.name == AudiobookPlaybackService::class.java.name })
    }

    @Test
    fun `builds one player and session with speech attributes (AND-STK-6)`() {
        // TC-92: the service builds one speech player and one session. The session's player is chapter-relative, which
        // hides the previous and next commands from the controller.
        val controller = connect()
        awaitUntil("the service's player and session") { bridge.player != null && bridge.mediaSession != null }

        val attributes = onMain { bridge.player!!.audioAttributes }
        assertEquals(C.USAGE_MEDIA, attributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_SPEECH, attributes.contentType)
        assertTrue(onMain { controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) })
        assertFalse(onMain { controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) })
    }

    @Test
    fun `releases on destroy (AND-STK-1)`() {
        // TC-92: when the last controller releases and the service stops, the player and session are released.
        // This needs no other connection to the service, so it relies on the other tests releasing theirs.
        val controller = connect()
        awaitUntil("the service's player") { bridge.player != null }
        val exo = onMain { bridge.player!! }

        onMain { controller.release() }
        controllers.remove(controller)
        context.stopService(Intent(context, AudiobookPlaybackService::class.java))

        awaitUntil("the service to detach its player") { bridge.player == null }
        assertTrue(onMain { exo.isReleased })
    }

    @Test
    fun `shows the chapter title, span duration and two custom actions (I-SYS-2, P-PLAY-2)`() {
        // TC-103: the platform session shows the chapter title and the span's duration, with back 15 and forward 30.
        val controller = connect()
        startBookAt(70_000)
        val platform = openPlatformController()

        awaitUntil("the platform session to show the chapter title") {
            platform.metadata?.getString(PlatformMetadata.METADATA_KEY_TITLE) == "Two"
        }
        assertEquals(50_000L, platform.metadata!!.getLong(PlatformMetadata.METADATA_KEY_DURATION))

        val actions = platform.playbackState?.customActions.orEmpty().map { it.action }
        assertTrue(actions.contains(AudiobookSessionCallback.SKIP_BACK_ACTION))
        assertTrue(actions.contains(AudiobookSessionCallback.SKIP_FORWARD_ACTION))
        onMain { controller.pause() }
    }

    @Test
    fun `reports speed 0 while paused (I-SYS-2)`() {
        // TC-103: the platform speed is 0 while paused, and the real rate while playing.
        val controller = connect()
        startBookAt(70_000)
        val platform = openPlatformController()

        awaitUntil("the platform session to report paused") { platform.playbackState?.playbackSpeed == 0f }

        onMain { controller.play() }
        awaitUntil("the platform session to report the rate while playing") {
            platform.playbackState?.playbackSpeed == 1f
        }

        onMain { controller.pause() }
        awaitUntil("the platform session to report paused again") { platform.playbackState?.playbackSpeed == 0f }
    }

    /** The platform controller for the service's session, which is what the system surfaces read. */
    private fun openPlatformController(): PlatformController {
        awaitUntil("the service's session") { bridge.mediaSession != null }
        val token = onMain { bridge.mediaSession!!.platformToken }
        return onMain { PlatformController(context, token) }
    }

    @Test
    fun `custom buttons skip through the filter (AND-STK-2)`() {
        // TC-104: the custom skip commands move the book by 30 s forward and 15 s back, through the press filter.
        val controller = connect()
        val exo = startBookAt(10_000)

        // The press filter treats presses within a second of each other as one, so wait between them.
        Thread.sleep(1_100)
        onMain { controller.sendCustomCommand(AudiobookSessionCallback.SKIP_FORWARD, Bundle.EMPTY) }
            .get(WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
        awaitUntil("forward 30 s to 40 s") { near(40_000, exo.currentPosition) }

        Thread.sleep(1_100)
        onMain { controller.sendCustomCommand(AudiobookSessionCallback.SKIP_BACK, Bundle.EMPTY) }
            .get(WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
        awaitUntil("back 15 s to 25 s") { near(25_000, exo.currentPosition) }
    }

    @Test
    fun `custom buttons are back 15 and forward 30 in their slots (AND-STK-2, P-PLAY-2)`() {
        // TC-104: the media button preferences are the two skip buttons, with their icons, slots and names.
        val controller = connect()
        awaitUntil("the media button preferences") { controller.mediaButtonPreferences.size == 2 }
        val buttons = onMain { controller.mediaButtonPreferences }

        val back = buttons.first { it.sessionCommand?.customAction == AudiobookSessionCallback.SKIP_BACK_ACTION }
        assertEquals(CommandButton.ICON_SKIP_BACK_15, back.icon)
        assertTrue(back.slots.contains(CommandButton.SLOT_BACK))
        assertEquals(context.getString(R.string.audiobook_playback_back_15), back.displayName.toString())

        val forward = buttons.first { it.sessionCommand?.customAction == AudiobookSessionCallback.SKIP_FORWARD_ACTION }
        assertEquals(CommandButton.ICON_SKIP_FORWARD_30, forward.icon)
        assertTrue(forward.slots.contains(CommandButton.SLOT_FORWARD))
        assertEquals(context.getString(R.string.audiobook_playback_forward_30), forward.displayName.toString())
    }

    @Test
    fun `a media notification is posted without a POST_NOTIFICATIONS prompt (AND-STK-2)`() {
        // TC-104: playing posts the media notification on the playback channel, with no permission request.
        val controller = connect()
        startBookAt(10_000)
        onMain { controller.play() }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelName = context.getString(R.string.audiobook_playback_notification_channel)
        awaitUntil("a media notification on the playback channel") {
            manager.activeNotifications.any { notification ->
                manager.getNotificationChannel(notification.notification.channelId)?.name?.toString() == channelName
            }
        }
        onMain { controller.pause() }
    }

    @Test
    fun `session activity opens the app`() {
        // TC-104: the session activity is an immutable PendingIntent.
        val controller = connect()
        awaitUntil("the session activity") { controller.sessionActivity != null }
        val activity = onMain { controller.sessionActivity }
        assertNotNull(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(activity!!.isImmutable)
        }
    }

    private companion object {
        val CHAPTERS = listOf(
            Chapter(1L, "One", 0.0, 60.0),
            Chapter(2L, "Two", 60.0, 50.0),
            Chapter(3L, "Three", 110.0, 20.0),
        )
    }
}
