package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeOfferStringsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // #166 TC-70: R10, R11, P-SYNC-6, P-SYNC-7: the offer copy is the spec's, Dismiss is C13's
    @Test
    fun offerStringsHaveTheSpecCopy() {
        assertEquals("Listened further elsewhere", context.getString(R.string.audiobook_offers_device_title))
        assertEquals("Another device left off at 6:38.", context.getString(R.string.audiobook_offers_device_detail, "6:38"))
        assertEquals("Read further in the ebook", context.getString(R.string.audiobook_offers_ebook_ahead_title))
        assertEquals("Re-reading earlier in the ebook", context.getString(R.string.audiobook_offers_ebook_behind_title))
        assertEquals("Your reading maps to about 6:38.", context.getString(R.string.audiobook_offers_ebook_detail, "6:38"))
        assertEquals("Go", context.getString(R.string.audiobook_offers_go))
        assertEquals("Dismiss", context.getString(R.string.audiobook_dismiss))
    }
}
