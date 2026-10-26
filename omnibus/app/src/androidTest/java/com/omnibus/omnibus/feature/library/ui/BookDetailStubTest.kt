package com.omnibus.omnibus.feature.library.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
                BookDetailStubScreen(uuid = "abc")
            }
        }

        rule.onNodeWithText("Book detail").assertIsDisplayed()
        rule.onNodeWithText("uuid: abc").assertIsDisplayed()
    }
}
