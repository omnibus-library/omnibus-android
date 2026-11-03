package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreButton
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreTestTags
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Rule
import org.junit.Test

class DeleteMenuEntryTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-60
    @Test
    fun issue121_tc60_theRegisteredMoreButtonComposesWithTheDeleteEntryBeforeTheMenuOpens() {
        rule.setDetailScreen(
            BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = BookDetailSlots(topBarActions = listOf(BookMoreButton)),
        )

        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).assertIsDisplayed()
        rule.onAllNodesWithTag(DeleteTestTags.SHEET).assertCountEquals(0)
    }
}
