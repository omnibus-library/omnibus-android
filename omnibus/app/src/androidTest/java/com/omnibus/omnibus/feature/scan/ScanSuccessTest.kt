package com.omnibus.omnibus.feature.scan

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScanSuccessTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-31
    @Test
    fun issue317_tc31_successNamesWhereTheCopyWent() {
        val book = scanBook(uuid = "new-1")
        val kind = mutableStateOf(SuccessKind.CheckedIn)
        val scanAnother = mutableListOf<Unit>()
        val viewed = mutableListOf<String>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(
                        stage = ScanStage.Success(book.title, SuccessCover.Library(book), book.uuid, kind.value),
                    ),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(onScanAnother = { scanAnother += Unit }, onViewBook = { viewed += it }),
                    wide = false,
                    reducedMotion = true,
                )
            }
        }

        rule.onNodeWithText(scanText(R.string.scan_success_checked_in)).assertIsDisplayed()
        rule.onAllNodesWithText(book.title).onFirst().assertIsDisplayed()

        rule.runOnIdle { kind.value = SuccessKind.OffWishlist }

        rule.onNodeWithText(scanText(R.string.scan_success_off_wishlist)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_scan_another)).performClick()
        rule.onNodeWithText(scanText(R.string.scan_view_book)).performClick()

        assertEquals(1, scanAnother.size)
        assertEquals(listOf("new-1"), viewed)
    }
}
