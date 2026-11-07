package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Renders [WishlistRemoveRow] for [row] inside a menu that is always open, and counts its taps. */
private fun ComposeContentTestRule.setRow(row: RemoveRow, onClick: () -> Unit) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                DropdownMenu(expanded = true, onDismissRequest = {}) {
                    WishlistRemoveRow(row = row, onClick = onClick)
                }
            }
        }
    }
}

class WishlistMenuRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val label get() = detailText(R.string.book_detail_wishlist_remove)
    private val needsConnection get() = detailText(R.string.book_detail_more_needs_connection)

    // TC-74
    @Test
    fun issue119_tc74_enabledRowRecordsOneTap() {
        var taps = 0
        rule.setRow(RemoveRow.Enabled) { taps++ }

        rule.onNodeWithTag(WishlistTestTags.MENU_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, taps)
    }

    // TC-76
    @Test
    fun issue119_tc76_offlineRowIgnoresTaps() {
        var taps = 0
        rule.setRow(RemoveRow.DisabledOffline) { taps++ }

        rule.onNodeWithTag(WishlistTestTags.MENU_ROW).assertIsNotEnabled()
        rule.onNodeWithTag(WishlistTestTags.MENU_ROW).performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(0, taps)
    }

    // TC-77
    @Test
    fun issue119_tc77_offlineRowIsAnnouncedWithItsLabelAndReason() {
        rule.setRow(RemoveRow.DisabledOffline) {}

        rule.onNodeWithTag(WishlistTestTags.MENU_ROW)
            .assertTextContains(label)
            .assertTextContains(needsConnection)
    }
}
