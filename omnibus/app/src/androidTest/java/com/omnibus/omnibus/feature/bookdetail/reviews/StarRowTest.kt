package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The star row: its one description per rating, and which parts of each star are drawn in the tint. */
class StarRowTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-45 (AC7)
    @Test
    fun tc45_describesTheRoundedRatingAsOneNode() {
        val ratings = listOf(4.5f, 0f, 5f, 3.3f, 3.2f)
        val descriptions = listOf("4.5 out of 5 stars", "0 out of 5 stars", "5 out of 5 stars", "3.5 out of 5 stars", "3 out of 5 stars")
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxWidth()) {
                    ratings.forEachIndexed { index, rating ->
                        StarRow(rating, 18.dp, Color.Red, Modifier.testTag("r${index + 1}"))
                    }
                }
            }
        }

        ratings.indices.forEach { index ->
            val tag = "r${index + 1}"
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(1)
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertContentDescriptionEquals(descriptions[index])
            val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(node.children.none { it.config.contains(SemanticsProperties.ContentDescription) })
        }
    }

    // TC-46 (AC1)
    @Test
    fun tc46_drawsFullHalfAndEmptySlotsMirroredInRtl() {
        val gap = with(rule.density) { 2.dp.toPx() }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth().background(Color.Black)) {
                    Column {
                        StarRow(2.5f, 24.dp, Color.Red, Modifier.testTag("ltr"))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            StarRow(2.5f, 24.dp, Color.Red, Modifier.testTag("rtl"))
                        }
                    }
                }
            }
        }

        val ltr = rule.onNodeWithTag("ltr", useUnmergedTree = true)
        val ltrSize = ltr.fetchSemanticsNode().size
        val star = (ltrSize.width - 4 * gap) / 5f
        val middle = 2 * (star + gap)
        val y = (0.55f * ltrSize.height).toInt()
        fun at(left: Float, fraction: Float) = (left + fraction * star).toInt()
        val ltrPixels = ltr.captureToImage().toPixelMap()

        // Slot 1 is full, slot 3 is half filled from its left edge, slot 5 is empty.
        assertTrue(ltrPixels[at(0f, 0.35f), y].isRed())
        assertTrue(ltrPixels[at(0f, 0.65f), y].isRed())
        assertTrue(ltrPixels[at(middle, 0.35f), y].isRed())
        assertFalse(ltrPixels[at(middle, 0.65f), y].isRed())
        assertFalse(ltrPixels[at(4 * (star + gap), 0.35f), y].isRed())
        assertFalse(ltrPixels[at(4 * (star + gap), 0.65f), y].isRed())

        val rtl = rule.onNodeWithTag("rtl", useUnmergedTree = true)
        val rtlPixels = rtl.captureToImage().toPixelMap()
        val rightmost = ltrSize.width - star

        // Mirrored: slot 1 is the rightmost, and the half fill is on the right of slot 3.
        assertTrue(rtlPixels[at(rightmost, 0.35f), y].isRed())
        assertTrue(rtlPixels[at(rightmost, 0.65f), y].isRed())
        assertFalse(rtlPixels[at(middle, 0.35f), y].isRed())
        assertTrue(rtlPixels[at(middle, 0.65f), y].isRed())
        assertFalse(rtlPixels[at(0f, 0.35f), y].isRed())
        assertFalse(rtlPixels[at(0f, 0.65f), y].isRed())
        assertEquals(ltrSize.width, rtl.fetchSemanticsNode().size.width)
    }

    /** Red in the tint, which is what a filled part of a star draws. */
    private fun Color.isRed() = red > 0.7f && green < 0.3f && blue < 0.3f
}
