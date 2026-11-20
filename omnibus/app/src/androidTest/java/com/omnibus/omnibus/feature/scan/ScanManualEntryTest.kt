package com.omnibus.omnibus.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScanManualEntryTest {

    @get:Rule
    val rule = createComposeRule()

    /** The manual stage with the typed text held here, as the ViewModel would hold it. */
    @Composable
    private fun ManualHarness(initial: String, onScanInstead: () -> Unit) {
        var text by remember { mutableStateOf(initial) }
        ScanScreenContent(
            uiState = ScanUIState(stage = ScanStage.Manual(text = text)),
            camera = FakeBarcodeCamera(),
            actions = ScanActions(onIsbnChanged = { text = it }, onScanInstead = onScanInstead),
            wide = false,
            reducedMotion = true,
        )
    }

    // TC-17
    @Test
    fun issue317_tc17_findBookWaitsForAValidIsbn() {
        var scanInstead = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ManualHarness(initial = "978-0-671-68357-4") { scanInstead++ }
            }
        }

        rule.onNodeWithText(scanText(R.string.scan_isbn_bad_check_digit)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_find_book)).assertIsNotEnabled()

        rule.onNodeWithTag("scan-manual-field").performTextReplacement("978-0-306-40615-7")
        rule.waitForIdle()

        rule.onNodeWithText(scanText(R.string.scan_find_book)).assertIsEnabled()
        rule.onNodeWithText(scanText(R.string.scan_scan_instead)).performClick()
        assertEquals(1, scanInstead)
    }
}
