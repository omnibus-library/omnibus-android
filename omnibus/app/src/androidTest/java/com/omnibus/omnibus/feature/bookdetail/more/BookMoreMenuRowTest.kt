package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookMoreMenuRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val enabledTag = "row-enabled"
    private val disabledTag = "row-disabled"
    private val label = "Add to shelf"
    private val needsConnection = detailText(R.string.book_detail_more_needs_connection)

    private var enabledClicks = 0
    private var disabledClicks = 0

    /** Renders one enabled and one disabled row inside a menu that is always open. */
    private fun setRows() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    DropdownMenu(expanded = true, onDismissRequest = {}) {
                        BookMoreMenuRow(
                            label = label,
                            icon = R.drawable.ic_book_detail_more_shelf,
                            enabled = true,
                            subLabel = null,
                            onClick = { enabledClicks++ },
                            modifier = Modifier.testTag(enabledTag),
                        )
                        BookMoreMenuRow(
                            label = "Bookmarks",
                            icon = R.drawable.ic_book_detail_more_shelf,
                            enabled = false,
                            subLabel = needsConnection,
                            onClick = { disabledClicks++ },
                            modifier = Modifier.testTag(disabledTag),
                        )
                    }
                }
            }
        }
    }

    // TC-15
    @Test
    fun issue116_tc15_disabledRowShowsNeedsConnectionAndIgnoresTaps() {
        setRows()

        rule.onNodeWithTag(disabledTag).assertIsNotEnabled()
        rule.onNodeWithText(needsConnection).assertIsDisplayed()
        rule.onNodeWithTag(disabledTag).performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(0, disabledClicks)
    }

    // TC-15
    @Test
    fun issue116_tc15_enabledRowHasNoSubLineAndRecordsOneClick() {
        setRows()

        rule.onNodeWithTag(enabledTag).assertIsEnabled().assertHasClickAction()
        rule.onAllNodesWithText(needsConnection).assertCountEquals(1)
        rule.onNodeWithTag(enabledTag).performClick()
        rule.waitForIdle()
        assertEquals(1, enabledClicks)
    }

    // TC-17
    @Test
    fun issue116_tc17_disabledRowIsAnnouncedAsDisabledWithItsReason() {
        setRows()

        rule.onNodeWithTag(disabledTag)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        val text = rule.onNodeWithTag(disabledTag).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue("announced text was '$text'", text.contains("Bookmarks") && text.contains(needsConnection))
    }
}
