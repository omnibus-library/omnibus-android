package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.DisplayFontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookStageTitleTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-81
    @Test
    fun issue93_tc81_titleStyleAndHeading() {
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(title = "The Left Hand of Darkness"), isLoading = false))

        val title = rule.onNodeWithTag(BookDetailTestTags.TITLE, useUnmergedTree = true)
        title.assert(isHeading())
        val style = title.textLayout().layoutInput.style
        assertEquals(DisplayFontFamily, style.fontFamily)
        assertEquals(FontWeight.SemiBold, style.fontWeight)
        assertEquals(TextAlign.Center, style.textAlign)
        assertEquals((-0.015).em, style.letterSpacing)
        assertEquals(0.98.em, style.lineHeight)
    }

    // TC-82
    @Test
    fun issue93_tc82_fittingTitleKeepsItsStep() {
        // 23 code points, so the 42 sp step applies and nothing shrinks or ellipsizes.
        val title = "Short Words Make Titles"
        assertEquals(23, title.codePointCount(0, title.length))
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(title = title), isLoading = false))

        val layout = rule.onNodeWithTag(BookDetailTestTags.TITLE, useUnmergedTree = true).textLayout()
        assertEquals(42f, layout.layoutInput.style.fontSize.value, 0.01f)
        assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) })
    }

    // TC-83
    @Test
    fun issue93_tc83_veryLongTitleEllipsizesOnTheFourthLine() {
        val title = List(30) { "Short words" }.joinToString(" ")
        assertTrue(title.length >= 300)
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(title = title), isLoading = false))

        val titleNode = rule.onNodeWithTag(BookDetailTestTags.TITLE, useUnmergedTree = true)
        val layout = titleNode.textLayout()
        assertEquals(4, layout.lineCount)
        assertTrue(layout.isLineEllipsized(3))
        val fontSize = layout.layoutInput.style.fontSize.value
        assertTrue("font size $fontSize sp", fontSize in 27f..38f)

        val root = rule.onRoot().bounds()
        val bounds = titleNode.bounds()
        assertTrue(bounds.left >= root.left && bounds.right <= root.right)
        assertTrue(rule.onNodeWithTag(BookDetailTestTags.AUTHOR).bounds().top >= bounds.bottom)
    }
}
