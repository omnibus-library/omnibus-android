package com.omnibus.omnibus.feature.library.addbooks

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddBooksSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val allEnabled = listOf(
        AddBooksOption(AddBooksOptionKind.Upload, enabled = true),
        AddBooksOption(AddBooksOptionKind.Scan, enabled = true),
        AddBooksOption(AddBooksOptionKind.Wishlist, enabled = true),
    )

    // TC-17
    @Test
    fun issue319_tc17_optionTapHidesThenReportsItsKind() {
        val selected = mutableListOf<AddBooksOptionKind>()
        val closes = mutableListOf<Unit>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddBooksSheet(options = allEnabled, onSelect = { selected += it }, onClose = { closes += Unit })
            }
        }
        val scan = AddBooksTestTags.option(AddBooksOptionKind.Scan)
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag(scan).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithTag(scan).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { selected.isNotEmpty() }
        rule.waitForIdle()

        assertEquals(listOf(AddBooksOptionKind.Scan), selected)
        assertEquals(0, closes.size)
    }

    // TC-18
    @Test
    fun issue319_tc18_doneClosesTheSheet() {
        val selected = mutableListOf<AddBooksOptionKind>()
        val closes = mutableListOf<Unit>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddBooksSheet(options = allEnabled, onSelect = { selected += it }, onClose = { closes += Unit })
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag(AddBooksTestTags.DONE).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithTag(AddBooksTestTags.DONE).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { closes.size == 1 }
        rule.waitForIdle()

        assertTrue("Done should not select an option", selected.isEmpty())
    }
}
