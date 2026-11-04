package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBookmarksStringsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // TC73, R7-R10, AND-UI-6: the player's bookmark strings carry the ticket's copy
    @Test
    fun bookmarksStringsMatchTicketCopy() {
        assertEquals("Sync here", context.getString(R.string.audiobook_bookmarks_sync_here))
        assertEquals("Synced ✓", context.getString(R.string.audiobook_bookmarks_synced))
        assertEquals("Needs a connection", context.getString(R.string.audiobook_bookmarks_needs_connection))
        assertEquals("Swipe one to remove it.", context.getString(R.string.audiobook_bookmarks_swipe_hint))
        assertEquals("Remove bookmark", context.getString(R.string.audiobook_bookmarks_remove))
    }
}
