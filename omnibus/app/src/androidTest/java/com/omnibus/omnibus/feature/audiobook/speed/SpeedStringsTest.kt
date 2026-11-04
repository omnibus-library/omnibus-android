package com.omnibus.omnibus.feature.audiobook.speed

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedStringsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // #168 TC-90: R20: every speed string resolves, the labels are the spec's copy, and the templates take their arguments
    @Test
    fun speedStringsResolveAndFormat() {
        val title = context.getString(R.string.audiobook_speed_title)
        val fineTune = context.getString(R.string.audiobook_speed_fine_tune)
        val sliderDescription = context.getString(R.string.audiobook_speed_slider_description)
        val slower = context.getString(R.string.audiobook_speed_slower)
        val faster = context.getString(R.string.audiobook_speed_faster)

        assertEquals("Playback speed", title)
        assertEquals("Fine-tune", fineTune)
        assertEquals("Fine-tune speed", sliderDescription)
        assertEquals("Slower", slower)
        assertEquals("Faster", faster)

        val range = context.getString(R.string.audiobook_speed_range, "0.5×", "3.0×")
        assertEquals("0.5× — 3.0×", range)

        val caption = context.getString(R.string.audiobook_speed_step_caption, "0.05")
        assertEquals("0.05 steps", caption)

        listOf(title, fineTune, sliderDescription, slower, faster, range, caption).forEach { text ->
            assertTrue("\"$text\" is not blank", text.isNotBlank())
            assertFalse("\"$text\" has an unfilled placeholder", text.contains('%'))
        }
    }
}
