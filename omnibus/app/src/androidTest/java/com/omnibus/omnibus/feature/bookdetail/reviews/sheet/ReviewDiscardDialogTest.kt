package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The discard confirmation: its copy, its two buttons, and the dismiss path, which keeps the draft. */
class ReviewDiscardDialogTest {

    @get:Rule
    val rule = createComposeRule()

    /** Shows the dialog with recording callbacks. */
    private fun show(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReviewDiscardDialog(onDiscard = onDiscard, onKeepEditing = onKeepEditing)
            }
        }
    }

    @Test
    fun `TC-101 the dialog says what is lost, with Discard and Keep editing`() {
        show(onDiscard = {}, onKeepEditing = {})

        rule.onNodeWithText("Discard your review?").assertExists()
        rule.onNodeWithText("Your stars and what you wrote here won't be saved.").assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.DISCARD).assertTextEquals("Discard")
        rule.onNodeWithTag(ReviewSheetTestTags.KEEP_EDITING).assertTextEquals("Keep editing")
    }

    @Test
    fun `TC-102 Discard and Keep editing each call back once, and dismissing keeps editing`() {
        var discards = 0
        var keeps = 0
        show(onDiscard = { discards++ }, onKeepEditing = { keeps++ })

        // The dialog is not driven by state here, so it stays up across the three actions.
        rule.onNodeWithTag(ReviewSheetTestTags.KEEP_EDITING).performClick()
        assertEquals(1, keeps)
        assertEquals(0, discards)

        rule.onNodeWithTag(ReviewSheetTestTags.DISCARD).performClick()
        assertEquals(1, discards)

        Espresso.pressBack()
        assertEquals(2, keeps)
        assertEquals(1, discards)
    }
}
