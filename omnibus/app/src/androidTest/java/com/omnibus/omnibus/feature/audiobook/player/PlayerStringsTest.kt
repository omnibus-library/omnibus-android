package com.omnibus.omnibus.feature.audiobook.player

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerStringsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // R19, C13: the shared labels exist with the contract's text
    @Test
    fun sharedStringsMatchContractC13() {
        assertEquals("Close player", context.getString(R.string.audiobook_close_player))
        assertEquals("Previous chapter", context.getString(R.string.audiobook_previous_chapter))
        assertEquals("Back 15 seconds", context.getString(R.string.audiobook_back_15))
        assertEquals("Play", context.getString(R.string.audiobook_play))
        assertEquals("Pause", context.getString(R.string.audiobook_pause))
        assertEquals("Forward 30 seconds", context.getString(R.string.audiobook_forward_30))
        assertEquals("Next chapter", context.getString(R.string.audiobook_next_chapter))
        assertEquals("6:38 left", context.getString(R.string.audiobook_time_left, "6:38"))
        assertEquals("Dismiss", context.getString(R.string.audiobook_dismiss))
    }

    // R19: the player's own strings are filled in
    @Test
    fun playerOwnedStringsAreNotBlank() {
        listOf(
            R.string.audiobook_player_open_book,
            R.string.audiobook_player_bookmarks,
            R.string.audiobook_player_chapters_action,
            R.string.audiobook_player_position_chapter,
            R.string.audiobook_player_position_book,
            R.string.audiobook_player_tool_speed,
            R.string.audiobook_player_tool_car_mode,
            R.string.audiobook_player_tool_sleep,
            R.string.audiobook_player_tool_bookmark,
            R.string.audiobook_player_preparing,
            R.string.audiobook_player_buffering,
            R.string.audiobook_player_retry,
            R.string.audiobook_player_error_no_audio_parts,
            R.string.audiobook_player_error_timeline,
            R.string.audiobook_player_error_part_unreadable,
            R.string.audiobook_player_error_stopped,
            R.string.audiobook_player_error_server,
            R.string.audiobook_player_offline_title,
            R.string.audiobook_player_offline_message,
            R.string.audiobook_player_offline_message_untitled,
            R.string.audiobook_player_ok,
        ).forEach { assertFalse(context.getString(it).isBlank()) }
    }
}
