package com.omnibus.omnibus.feature.scan

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class ScanTitleSearchTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setStage(stage: ScanStage.TitleSearch) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(stage = stage),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(),
                    wide = false,
                    reducedMotion = true,
                )
            }
        }
    }

    // TC-59a
    @Test
    fun issue335_tc59a_resultsListTitleAndAuthorWithOneField() {
        val records = listOf(scanRecord(title = "Piranesi"), scanRecord(title = "Jonathan Strange"), scanRecord(title = "Dune"))
        setStage(ScanStage.TitleSearch(query = "piranesi", results = records))

        records.forEach { record ->
            rule.onNodeWithText(record.title).assertIsDisplayed()
        }
        rule.onAllNodes(hasText("Susanna Clarke", substring = true)).assertCountEquals(records.size)
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onNodeWithText("Showing", substring = true).assertDoesNotExist()
    }

    // TC-59b
    @Test
    fun issue335_tc59b_emptyResultsSayNoBookMatches() {
        setStage(ScanStage.TitleSearch(query = "zzz", results = emptyList()))

        rule.onNodeWithText(scanText(R.string.scan_no_title_matches)).assertIsDisplayed()
        rule.onNode(hasText(scanText(R.string.scan_scan_instead))).assertIsDisplayed()
    }
}
