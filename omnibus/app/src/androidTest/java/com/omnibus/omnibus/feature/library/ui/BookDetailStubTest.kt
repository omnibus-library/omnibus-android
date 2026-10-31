package com.omnibus.omnibus.feature.library.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookDetailStubTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc76_routeRoundTripsThroughJson() {
        val route = BookDetailRoute("abc")

        val decoded = Json.decodeFromString(
            BookDetailRoute.serializer(),
            Json.encodeToString(BookDetailRoute.serializer(), route),
        )

        assertEquals(route, decoded)
    }

    @Test
    fun tc76_stubShowsTitleAndUuid() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailStubScreen(uuid = "abc", onRead = {})
            }
        }

        rule.onNodeWithText("Book detail").assertIsDisplayed()
        rule.onNodeWithText("uuid: abc").assertIsDisplayed()
    }

    @Test
    fun tc61_showsAReadButtonThatOpensTheReader() {
        val received = mutableListOf<String>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailStubScreen(uuid = "7f3c2a9e", onRead = { received += it })
            }
        }

        rule.onNodeWithText("Read")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf("7f3c2a9e"), received)
    }

    @Test
    fun tc63_readPassesTheBookUuid() {
        val received = mutableListOf<String>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailStubScreen(uuid = "abc", onRead = { received += it })
            }
        }

        rule.onNodeWithText("Read").performClick()

        assertEquals(listOf("abc"), received)
    }
}
