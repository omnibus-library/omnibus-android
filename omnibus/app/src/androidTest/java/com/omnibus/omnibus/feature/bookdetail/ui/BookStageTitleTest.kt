package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.DisplayFontFamily
import org.junit.Assert.assertEquals
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
}
