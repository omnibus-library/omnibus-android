package com.omnibus.omnibus.data.audiobook.playback.media3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.RemoteTransport

/**
 * The session's callback (AND-STK-2, P-PLAY-6): it offers the two custom skip buttons, runs them through the press filter,
 * and routes the media keys. Play and pause are left to Media3.
 */
@OptIn(UnstableApi::class)
internal class AudiobookSessionCallback(private val player: ChapterForwardingPlayer) : MediaSession.Callback {

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SKIP_BACK).add(SKIP_FORWARD).build(),
            )
            .build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            SKIP_BACK_ACTION -> player.remoteMove(RemoteTransport.SkipBackward)
            SKIP_FORWARD_ACTION -> player.remoteMove(RemoteTransport.SkipForward)
            else -> return super.onCustomCommand(session, controller, customCommand, args)
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        val key = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
        val transport = RemoteTransport.forMediaKey(key.keyCode) ?: return false
        if (RemoteTransport.actsOn(key.action, key.repeatCount)) player.remoteMove(transport)
        return true
    }

    companion object {
        const val SKIP_BACK_ACTION = "com.omnibus.omnibus.audiobook.SKIP_BACK"
        const val SKIP_FORWARD_ACTION = "com.omnibus.omnibus.audiobook.SKIP_FORWARD"
        val SKIP_BACK = SessionCommand(SKIP_BACK_ACTION, Bundle.EMPTY)
        val SKIP_FORWARD = SessionCommand(SKIP_FORWARD_ACTION, Bundle.EMPTY)

        /** The notification's back 15 and forward 30 buttons, which the session publishes as its media button preferences. */
        fun buttons(context: Context): List<CommandButton> = listOf(
            CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
                .setDisplayName(context.getString(R.string.audiobook_playback_back_15))
                .setSessionCommand(SKIP_BACK)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
                .setDisplayName(context.getString(R.string.audiobook_playback_forward_30))
                .setSessionCommand(SKIP_FORWARD)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )
    }
}
