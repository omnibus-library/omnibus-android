package com.omnibus.omnibus.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StateViewsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc42_errorRetryInvokesTheCallback() {
        var retries = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ErrorStateView(
                    title = "Couldn't load this",
                    message = "Could not connect to the server.",
                    actionLabel = "Try again",
                    onRetry = { retries++ },
                )
            }
        }

        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun tc44_emptyOptionalActionInvokesCallback() {
        var taps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                EmptyStateView(
                    title = "No books yet",
                    message = "Add a book to get started.",
                    action = StateAction(label = "Add books", onClick = { taps++ }),
                )
            }
        }

        rule.onNodeWithText("Add books").performClick()
        assertEquals(1, taps)
    }
}
