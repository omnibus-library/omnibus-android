package com.omnibus.omnibus.feature.audiobook.sleep

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sleep strings resolve to their English text, with the plural for hours (R16). */
class SleepStringsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // TC-42, R16: the sheet's and the pill's strings, and the hours plural, resolve to the English copy
    @Test
    fun issue169_tc42_sleepStringsResolveToTheEnglishCopy() {
        assertEquals("Sleep timer", context.getString(R.string.audiobook_sleep_title))
        assertEquals("Off", context.getString(R.string.audiobook_sleep_off))
        assertEquals("15 min", context.getString(R.string.audiobook_sleep_minutes, 15))
        assertEquals("1 hour", context.resources.getQuantityString(R.plurals.audiobook_sleep_hours, 1, 1))
        assertEquals("2 hours", context.resources.getQuantityString(R.plurals.audiobook_sleep_hours, 2, 2))
        assertEquals("End of chapter 3", context.getString(R.string.audiobook_sleep_end_of_chapter, 3))
        assertEquals("0:14:59 remaining", context.getString(R.string.audiobook_sleep_remaining, "0:14:59"))
        assertEquals(
            "Sleep timer, 14:32 remaining",
            context.getString(R.string.audiobook_sleep_pill_description, "14:32"),
        )
    }
}
