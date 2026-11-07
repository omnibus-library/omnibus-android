package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookMoreMenuRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val disabledTag = "row-disabled"

    private val needsConnection = detailText(R.string.book_detail_more_needs_connection)

    /** Renders a disabled row inside a menu that is always open. */
    private fun setRow() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    DropdownMenu(expanded = true, onDismissRequest = {}) {
                        BookMoreMenuRow(
                            label = "Bookmarks",
                            icon = R.drawable.ic_book_detail_more_shelf,
                            enabled = false,
                            subLabel = needsConnection,
                            onClick = {},
                            modifier = Modifier.testTag(disabledTag),
                        )
                    }
                }
            }
        }
    }

    // TC-17
    @Test
    fun issue116_tc17_disabledRowIsAnnouncedAsDisabledWithItsReason() {
        setRow()

        rule.onNodeWithTag(disabledTag)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        val text = rule.onNodeWithTag(disabledTag).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue("announced text was '$text'", text.contains("Bookmarks") && text.contains(needsConnection))
    }
}
