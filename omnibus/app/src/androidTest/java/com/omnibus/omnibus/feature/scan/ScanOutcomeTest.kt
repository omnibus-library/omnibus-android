package com.omnibus.omnibus.feature.scan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScanOutcomeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent(
        outcome: ScanOutcome,
        note: String = "",
        writing: WritingAction? = null,
        writeFailed: Boolean = false,
        actions: ScanActions = ScanActions(),
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanOutcomeStage(
                    outcome = outcome,
                    note = note,
                    writing = writing,
                    writeFailed = writeFailed,
                    actions = actions,
                    wide = false,
                )
            }
        }
    }

    // TC-25a
    @Test
    fun issue317_tc25a_inLibraryUnownedOffersCheckIn() {
        setContent(ScanOutcome.InLibraryUnowned(scanBook(hasFiles = true)))

        rule.onNodeWithText(scanText(R.string.scan_badge_in_library)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_in_library_body)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_edition_note)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_check_in)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_scan_another)).assertIsDisplayed()
    }

    // TC-25b
    @Test
    fun issue317_tc25b_onWishlistOffersCheckInAndGoToBookDetails() {
        val opened = mutableListOf<String>()
        setContent(
            ScanOutcome.OnWishlist(scanBook(uuid = "wish-1")),
            actions = ScanActions(onViewBook = { opened += it }),
        )

        rule.onNodeWithText(scanText(R.string.scan_badge_on_wishlist)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_on_wishlist_body)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_edition_note)).assertDoesNotExist()
        rule.onNodeWithText(scanText(R.string.scan_check_in)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_scan_another)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_go_to_book_details)).performClick()

        assertEquals(listOf("wish-1"), opened)
    }

    // TC-25c
    @Test
    fun issue317_tc25c_alreadyOwnedOffersViewBook() {
        val opened = mutableListOf<String>()
        setContent(
            ScanOutcome.AlreadyOwned(scanBook(uuid = "owned-1")),
            actions = ScanActions(onViewBook = { opened += it }),
        )

        rule.onNodeWithText(scanText(R.string.scan_badge_already_owned)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_already_owned_body)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_check_in)).assertDoesNotExist()
        rule.onNodeWithText(scanText(R.string.scan_view_book)).performClick()

        assertEquals(listOf("owned-1"), opened)
    }

    // TC-25d
    @Test
    fun issue317_tc25d_unplacedOffersScanAnotherOnly() {
        setContent(ScanOutcome.Unplaced)

        rule.onNodeWithText(scanText(R.string.scan_cant_place_title)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_cant_place_body)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_scan_another)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_check_in)).assertDoesNotExist()
    }

    // TC-29a
    @Test
    fun issue317_tc29a_actionsDisabledWhileSaving() {
        setContent(ScanOutcome.InLibraryUnowned(scanBook()), writing = WritingAction.CheckIn)

        rule.onNodeWithContentDescription(scanText(R.string.scan_saving)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_scan_another)).assertIsNotEnabled()
        rule.onNodeWithTag(ScanTestTags.BACK).assertIsNotEnabled()
    }

    // TC-29b
    @Test
    fun issue317_tc29b_saveFailureShowsUnderTheCard() {
        setContent(ScanOutcome.InLibraryUnowned(scanBook()), writeFailed = true)

        val error = rule.onNodeWithTag(ScanTestTags.WRITE_ERROR).assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val card = rule.onNodeWithTag(ScanTestTags.CARD).getUnclippedBoundsInRoot()
        val firstAction = rule.onNodeWithText(scanText(R.string.scan_check_in)).getUnclippedBoundsInRoot()

        assertTrue(error.top >= card.bottom)
        assertTrue(error.bottom <= firstAction.top)
        rule.onNodeWithText(scanText(R.string.scan_check_in)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_save_failed)).assertIsDisplayed()
    }

    // TC-37
    @Test
    fun issue317_tc37_closeAndBackAreDistinct() {
        var closed = 0
        var back = 0
        setContent(
            ScanOutcome.InLibraryUnowned(scanBook()),
            actions = ScanActions(onClose = { closed++ }, onBack = { back++ }),
        )

        rule.onNodeWithTag(ScanTestTags.CLOSE).performClick()
        assertEquals(1, closed)
        assertEquals(0, back)

        rule.onNodeWithTag(ScanTestTags.BACK).performClick()
        assertEquals(1, closed)
        assertEquals(1, back)
    }
}
