package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val NOW_MILLIS = 1_793_620_800_000L

class HighlightActionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val quoted = mutableListOf<String>()
    private val shared = mutableListOf<String>()

    /** The list with [rows], each footer carrying [HighlightRowActions] that record their passage. */
    private fun setContent(vararg rows: HighlightRowModel) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HighlightsContent(
                        HighlightsUIState(HighlightsUIContent.Loaded(rows.toList()), NOW_MILLIS),
                        onRetry = {},
                        rowActions = { HighlightRowActions(it, onQuoteCard = quoted::add, onShare = shared::add) },
                    )
                }
            }
        }
    }

    @Test
    fun rowActions_showAButtonOnlyOnARowWithAPassage() {
        setContent(testRow(1, text = "A passage"), testRow(2, text = null, note = "Kobo note"))

        rule.onAllNodesWithContentDescription("Highlight actions").assertCountEquals(1)
    }

    @Test
    fun theButton_opensTheMenuOfQuoteCardAndShare() {
        setContent(testRow(1, text = "A passage"))

        rule.onNodeWithContentDescription("Highlight actions").performClick()

        rule.onNodeWithText("Make a quote card").assertIsDisplayed()
        rule.onNodeWithText("Share").assertIsDisplayed()
    }

    @Test
    fun makeAQuoteCard_hands_thatRowsPassage_andClosesTheMenu() {
        setContent(testRow(1, text = "First"), testRow(2, text = "Second"))

        rule.onAllNodesWithContentDescription("Highlight actions")[1].performClick()
        rule.onNodeWithText("Make a quote card").performClick()

        assertEquals(listOf("Second"), quoted)
        assertEquals(emptyList<String>(), shared)
        rule.onNodeWithText("Make a quote card").assertDoesNotExist()
    }

    @Test
    fun share_hands_thatRowsPassage_andClosesTheMenu() {
        setContent(testRow(1, text = "First"))

        rule.onNodeWithContentDescription("Highlight actions").performClick()
        rule.onNodeWithText("Share").performClick()

        assertEquals(listOf("First"), shared)
        assertEquals(emptyList<String>(), quoted)
        rule.onNodeWithText("Share").assertDoesNotExist()
    }
}
