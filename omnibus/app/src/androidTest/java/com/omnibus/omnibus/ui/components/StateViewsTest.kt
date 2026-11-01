package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

    @Test
    fun tc46_loadingFillsContainer() = assertFillsContainer { modifier ->
        LoadingStateView(label = "Loading your library", modifier = modifier)
    }

    @Test
    fun tc46_errorFillsContainer() = assertFillsContainer { modifier ->
        ErrorStateView(
            title = "Couldn't load this",
            message = "Could not connect to the server.",
            actionLabel = "Try again",
            onRetry = {},
            modifier = modifier,
        )
    }

    @Test
    fun tc46_emptyFillsContainer() = assertFillsContainer { modifier ->
        EmptyStateView(
            kicker = "YOUR LIBRARY",
            title = "No books yet",
            message = "Point the server at a library folder, or add a book from the You tab.",
            modifier = modifier,
        )
    }

    /**
     * Puts the state view in a container smaller than the window on both axes, sized as a fraction
     * of it so the check holds on any screen, and asserts the view takes the container's full bounds.
     */
    private fun assertFillsContainer(stateView: @Composable (Modifier) -> Unit) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.6f).testTag(CONTAINER)) {
                    stateView(Modifier.testTag(STATE))
                }
            }
        }

        val container = rule.onNodeWithTag(CONTAINER).getUnclippedBoundsInRoot()
        rule.onNodeWithTag(STATE).assertIsDisplayed()
            .assertWidthIsEqualTo(container.right - container.left)
            .assertHeightIsEqualTo(container.bottom - container.top)
    }

    private companion object {
        const val CONTAINER = "container"
        const val STATE = "state"
    }
}
