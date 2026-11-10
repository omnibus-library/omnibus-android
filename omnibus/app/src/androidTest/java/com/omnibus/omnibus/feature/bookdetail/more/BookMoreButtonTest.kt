package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ShelfSheetTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class BookMoreButtonTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-1
    @Test
    fun issue116_tc1_moreButtonIsALabelledButton() {
        rule.setMoreButton(listOf(TestEntry("Row")))

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON)
            .assert(hasContentDescription(detailText(R.string.book_detail_more)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
    }

    // TC-14
    @Test
    fun issue270_tc14_moreButtonIsAtLeast48dp() {
        rule.setMoreButton(listOf(TestEntry("Row")))

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON)
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    // TC-67
    @Test
    fun issue116_tc67_menuOpenStateIsRestored() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            OmnibusTheme(darkTheme = true) {
                BookMoreButtonContent(testSectionContext(), listOf(TestEntry("Row")))
            }
        }
        rule.onAllNodesWithTag(BookMoreTestTags.MENU).assertCountEquals(0)

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()
    }

    // TC-71
    @Test
    fun issue116_tc71_entriesAreActivatedOnceTheMenuHasOpened() {
        rule.setMoreButton(
            listOf(
                TestEntry(
                    label = "Row",
                    overlay = { Text("activated=${LocalMoreMenuActivated.current}") },
                ),
            ),
        )
        rule.onNodeWithText("activated=false").assertIsDisplayed()

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.onNodeWithText("activated=true").assertIsDisplayed()

        rule.pressBackKey()
        rule.onNodeWithText("activated=true").assertIsDisplayed()
    }

    // TC-72
    @Test
    fun issue116_tc72_registeredMoreButtonRendersWithoutHiltUntilTheMenuOpens() {
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = BookDetailSlots(topBarActions = listOf(BookMoreButton)),
        )

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).assertIsDisplayed()
        rule.onAllNodesWithTag(ShelfSheetTestTags.SHEET).assertCountEquals(0)
    }
}
