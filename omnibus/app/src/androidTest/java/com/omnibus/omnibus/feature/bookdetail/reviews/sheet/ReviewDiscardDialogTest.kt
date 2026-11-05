package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The discard confirmation's two buttons and the dismiss path, which keeps the draft. ReviewDiscardDialogScreenshotTest covers how it looks. */
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
    fun tc102_DiscardAndKeepEditingEachCallBackOnceAndDismissingKeepsEditing() {
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
