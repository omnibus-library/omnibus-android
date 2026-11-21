package com.omnibus.omnibus.feature.scan

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ScanLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-39
    @Test
    fun issue317_tc39_wideViewfinderIsCentredWithButtonsBelow() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(),
                    wide = true,
                    reducedMotion = true,
                )
            }
        }

        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val viewfinder = rule.onNodeWithTag(ScanTestTags.VIEWFINDER).getUnclippedBoundsInRoot()
        val enterIsbn = rule.onNodeWithText(scanText(R.string.scan_enter_isbn)).getUnclippedBoundsInRoot()
        val leftGap = viewfinder.left - root.left
        val rightGap = root.right - viewfinder.right
        val gapDifferencePx = with(rule.density) { abs((leftGap - rightGap).toPx()) }

        assertTrue("the viewfinder's gaps differ by $gapDifferencePx px", gapDifferencePx <= 1f)
        assertTrue("the viewfinder is wider than its cap", viewfinder.right - viewfinder.left <= 480.dp)
        assertTrue("Enter ISBN is not below the viewfinder", enterIsbn.top >= viewfinder.bottom)
    }

    // TC-40a
    @Test
    fun issue317_tc40a_wideOutcomeIsTwoPanes() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanOutcomeStage(
                    outcome = ScanOutcome.InLibraryUnowned(scanBook()),
                    note = "",
                    writing = null,
                    writeFailed = false,
                    actions = ScanActions(),
                    wide = true,
                )
            }
        }

        val card = rule.onNodeWithTag(ScanTestTags.CARD).getUnclippedBoundsInRoot()
        val checkIn = rule.onNodeWithText(scanText(R.string.scan_check_in)).getUnclippedBoundsInRoot()

        assertTrue("the card overlaps Check in this copy", card.right <= checkIn.left)
    }

    // TC-54
    @Test
    fun issue336_tc54_wideNotInLibraryIsTwoPanes() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanOutcomeStage(
                    outcome = ScanOutcome.NotInLibrary(scanRecord()),
                    note = "",
                    writing = null,
                    writeFailed = false,
                    actions = ScanActions(),
                    wide = true,
                )
            }
        }

        val card = rule.onNodeWithTag(ScanTestTags.CARD).getUnclippedBoundsInRoot()
        val addPhysical = rule.onNodeWithText(scanText(R.string.scan_add_physical)).getUnclippedBoundsInRoot()

        assertTrue("the card overlaps Add as physical book", card.right <= addPhysical.left)
    }

    // TC-40b
    @Test
    fun issue317_tc40b_narrowOutcomeIsOneColumn() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanOutcomeStage(
                    outcome = ScanOutcome.InLibraryUnowned(scanBook()),
                    note = "",
                    writing = null,
                    writeFailed = false,
                    actions = ScanActions(),
                    wide = false,
                )
            }
        }

        val card = rule.onNodeWithTag(ScanTestTags.CARD).getUnclippedBoundsInRoot()
        val checkIn = rule.onNodeWithText(scanText(R.string.scan_check_in)).getUnclippedBoundsInRoot()

        assertTrue("Check in this copy is not below the card", checkIn.top >= card.bottom)
    }

    // TC-71a
    @Test
    fun issue335_tc71a_recoveryOutcomesAreTwoPanes() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanOutcomeStage(
                    outcome = ScanOutcome.CloseMatch(listOf(scanBook()), scanRecord()),
                    note = "",
                    writing = null,
                    writeFailed = false,
                    actions = ScanActions(),
                    wide = true,
                )
            }
        }

        val card = rule.onNodeWithTag(ScanTestTags.CARD).getUnclippedBoundsInRoot()
        val pick = rule.onNode(hasText(scanText(R.string.scan_yes_thats_it)) and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue("the close match's card overlaps its pick", card.right <= pick.left)
    }

    // TC-71a
    @Test
    fun issue335_tc71a_unresolvedIsTwoPanes() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanOutcomeStage(
                    outcome = ScanOutcome.Unresolved,
                    note = "",
                    writing = null,
                    writeFailed = false,
                    actions = ScanActions(),
                    wide = true,
                )
            }
        }

        val title = rule.onNodeWithTag(ScanTestTags.TITLE).getUnclippedBoundsInRoot()
        val search = rule.onNode(hasText(scanText(R.string.scan_search_by_title)) and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue("the message overlaps the title search", title.right <= search.left)
    }

    // TC-71b
    @Test
    fun issue335_tc71b_titleSearchColumnIsCentred() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(stage = ScanStage.TitleSearch()),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(),
                    wide = true,
                    reducedMotion = true,
                )
            }
        }

        assertCentredNarrowerThanRoot(rule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot())
    }

    // TC-71b
    @Test
    fun issue335_tc71b_libraryPickerColumnIsCentred() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(
                        stage = ScanStage.LibraryPicker(
                            returnTo = ScanStage.Outcome(ScanOutcome.Unresolved, "9781635575636", FoundVia.Scan),
                        ),
                    ),
                    camera = FakeBarcodeCamera(),
                    actions = ScanActions(),
                    wide = true,
                    reducedMotion = true,
                )
            }
        }

        assertCentredNarrowerThanRoot(rule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot())
    }

    /** The column holding [field] sits in the middle of the root, and is narrower than it. */
    private fun assertCentredNarrowerThanRoot(field: DpRect) {
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val leftGap = field.left - root.left
        val rightGap = root.right - field.right
        val gapDifferencePx = with(rule.density) { abs((leftGap - rightGap).toPx()) }

        assertTrue("the search column's gaps differ by $gapDifferencePx px", gapDifferencePx <= 1f)
        assertTrue("the search column is as wide as the root", field.right - field.left < root.right - root.left)
    }
}
