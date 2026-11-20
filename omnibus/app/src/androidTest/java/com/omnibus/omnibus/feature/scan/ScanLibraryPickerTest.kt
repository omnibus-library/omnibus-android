package com.omnibus.omnibus.feature.scan

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScanLibraryPickerTest {

    @get:Rule
    val rule = createComposeRule()

    private val returnTo = ScanStage.Outcome(ScanOutcome.Unresolved, "9781635575636", FoundVia.Scan)

    private fun hit(index: Int) = LibraryHit(
        uuid = "book-$index",
        title = "Library book $index",
        authorDisplay = "Author $index",
        year = null,
        formats = listOf("EPUB"),
        coverUrl = null,
        accent = null,
    )

    // TC-68a
    @Test
    fun issue335_tc68a_pickerNotesTruncation() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(
                        stage = ScanStage.LibraryPicker(
                            query = "lo",
                            hits = List(5) { hit(it) },
                            total = 23,
                            returnTo = returnTo,
                        ),
                    ),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(),
                    wide = false,
                    reducedMotion = true,
                )
            }
        }

        rule.onNodeWithText(scanText(R.string.scan_picker_title)).assertIsDisplayed()
        (0 until 5).forEach { rule.onNodeWithText("Library book $it").assertIsDisplayed() }
        rule.onNodeWithText(scanText(R.string.scan_picker_truncated).format(5, 23)).assertIsDisplayed()
    }

    // TC-68a
    @Test
    fun issue335_tc68a_pickerOmitsTheNoteWhenEveryMatchIsShown() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(
                        stage = ScanStage.LibraryPicker(query = "lo", hits = List(5) { hit(it) }, total = 5, returnTo = returnTo),
                    ),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(),
                    wide = false,
                    reducedMotion = true,
                )
            }
        }

        rule.onNodeWithText(scanText(R.string.scan_picker_truncated).format(5, 5)).assertDoesNotExist()
    }

    // TC-68b
    @Test
    fun issue335_tc68b_alreadyHaveIsOnThreeOutcomes() {
        var opened = 0
        val stage = mutableStateOf<ScanStage>(ScanStage.Viewfinder())
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(stage = stage.value),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(onAlreadyHave = { opened++ }),
                    wide = false,
                    reducedMotion = true,
                )
            }
        }
        val outcomes = listOf(
            ScanOutcome.CloseMatch(listOf(scanBook()), scanRecord()),
            ScanOutcome.NotInLibrary(scanRecord()),
            ScanOutcome.Unresolved,
        )

        outcomes.forEach { outcome ->
            rule.runOnIdle { stage.value = ScanStage.Outcome(outcome, "9781635575636", FoundVia.Scan) }
            rule.onNodeWithText(scanText(R.string.scan_already_have)).assertIsDisplayed().performClick()
        }

        assertEquals(outcomes.size, opened)
    }
}
