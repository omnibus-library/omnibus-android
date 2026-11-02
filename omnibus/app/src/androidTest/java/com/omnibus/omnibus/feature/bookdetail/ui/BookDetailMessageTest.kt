package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookDetailMessageTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-120
    @Test
    fun issue93_tc120_sectionMessagesShowAsSnackbars() {
        var undos = 0
        val slots = BookDetailSlots(
            stageStatusStart = listOf(BookDetailSection { ctx, modifier ->
                Text(
                    text = "show-saved",
                    modifier = modifier.testTag("show-saved").clickable {
                        ctx.onShowMessage(BookDetailMessage("Saved", actionLabel = "Undo", onAction = { undos++ }))
                    },
                )
                Text(
                    text = "show-held",
                    modifier = Modifier.testTag("show-held").clickable {
                        ctx.onShowMessage(BookDetailMessage("Held", holdUntilDismissed = true))
                    },
                )
            }),
        )
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(), isLoading = false), slots = slots)

        rule.onNodeWithTag("show-saved").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Saved").assertIsDisplayed()
        rule.onNodeWithText("Undo").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, undos)

        rule.onNodeWithTag("show-held").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Held").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dismiss").assertIsDisplayed()
    }
}
