package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StateViewsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc40_loadingShowsLabel() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                LoadingStateView(label = "Loading your library")
            }
        }

        rule.onNodeWithText("Loading your library").assertIsDisplayed()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(1)
    }

    @Test
    fun tc41_errorShowsTitleMessageAction() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ErrorStateView(
                    title = "Couldn't load this",
                    message = "Could not connect to the server.",
                    actionLabel = "Try again",
                    onRetry = {},
                )
            }
        }

        rule.onNodeWithText("Couldn't load this").assertIsDisplayed()
        rule.onNodeWithText("Could not connect to the server.").assertIsDisplayed()
        rule.onNodeWithText("Try again").assertIsDisplayed()
    }

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
    fun tc45_textIsParameterised() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        LoadingStateView(label = "Fetching shelves")
                    }
                    Box(Modifier.weight(1f)) {
                        ErrorStateView(
                            title = "Something broke",
                            message = "Retry later.",
                            actionLabel = "Retry now",
                            onRetry = {},
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        EmptyStateView(
                            kicker = "CUSTOM KICKER",
                            title = "Custom title",
                            message = "Custom message.",
                            action = StateAction(label = "Custom action", onClick = {}),
                        )
                    }
                }
            }
        }

        rule.onAllNodesWithText("Fetching shelves").assertCountEquals(1)
        rule.onAllNodesWithText("Something broke").assertCountEquals(1)
        rule.onAllNodesWithText("Retry later.").assertCountEquals(1)
        rule.onAllNodesWithText("Retry now").assertCountEquals(1)
        rule.onAllNodesWithText("CUSTOM KICKER").assertCountEquals(1)
        rule.onAllNodesWithText("Custom title").assertCountEquals(1)
        rule.onAllNodesWithText("Custom message.").assertCountEquals(1)
        rule.onAllNodesWithText("Custom action").assertCountEquals(1)

        rule.onAllNodesWithText("Loading your library").assertCountEquals(0)
        rule.onAllNodesWithText("Couldn't load this").assertCountEquals(0)
        rule.onAllNodesWithText("No books yet").assertCountEquals(0)
    }

    @Test
    fun tc46_loadingFillsContainer() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(300.dp, 600.dp)) {
                    LoadingStateView(
                        label = "Loading your library",
                        modifier = Modifier.testTag("state"),
                    )
                }
            }
        }

        rule.onNodeWithTag("state")
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(600.dp)
    }

    @Test
    fun tc46_errorFillsContainer() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(300.dp, 600.dp)) {
                    ErrorStateView(
                        title = "Couldn't load this",
                        message = "Could not connect to the server.",
                        actionLabel = "Try again",
                        onRetry = {},
                        modifier = Modifier.testTag("state"),
                    )
                }
            }
        }

        rule.onNodeWithTag("state")
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(600.dp)
    }

    @Test
    fun tc46_emptyFillsContainer() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(300.dp, 600.dp)) {
                    EmptyStateView(
                        kicker = "YOUR LIBRARY",
                        title = "No books yet",
                        message = "Point the server at a library folder, or add a book from the You tab.",
                        modifier = Modifier.testTag("state"),
                    )
                }
            }
        }

        rule.onNodeWithTag("state")
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(600.dp)
    }
}
