package com.omnibus.omnibus.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
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
    fun tc42_errorRetryInvokedOncePerClick() {
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

        rule.onNodeWithText("Try again").performClick()
        assertEquals(2, retries)
    }

    @Test
    fun tc43_emptyShowsCopyAndNoActionByDefault() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                EmptyStateView(
                    kicker = "YOUR LIBRARY",
                    title = "No books yet",
                    message = "Point the server at a library folder, or add a book from the You tab.",
                )
            }
        }

        rule.onNodeWithText("YOUR LIBRARY").assertIsDisplayed()
        rule.onNodeWithText("No books yet").assertIsDisplayed()
        rule.onNodeWithText(
            "Point the server at a library folder, or add a book from the You tab.",
        ).assertIsDisplayed()
        rule.onAllNodes(hasClickAction()).assertCountEquals(0)
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

        rule.onNodeWithText("Add books").assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }
}
