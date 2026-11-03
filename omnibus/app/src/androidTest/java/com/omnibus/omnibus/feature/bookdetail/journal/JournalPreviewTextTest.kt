package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JournalPreviewTextTest {

    // TC-57
    @Test
    fun tc57_veiledRunIsTransparentFillerWithABlurredShadow() {
        val text = journalPreviewText(journalPreview("He was ||Ares||."), veilColor = Color.Red, blurRadiusPx = 8f)

        assertEquals("He was lore.", text.text)
        assertEquals(1, text.spanStyles.size)
        val span = text.spanStyles.single()
        assertEquals(7, span.start)
        assertEquals(11, span.end)
        assertEquals(Color.Transparent, span.item.color)
        assertEquals(Shadow(Color.Red, Offset.Zero, 8f), span.item.shadow)
        assertFalse(text.text.contains("Ares"))
    }
}
