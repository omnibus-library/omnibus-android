package com.omnibus.omnibus.feature.libraries.card

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.you.YouScreenContent
import com.omnibus.omnibus.feature.you.YouScreenTestTags
import com.omnibus.omnibus.feature.you.YouUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountCardTest {

    @get:Rule
    val rule = createComposeRule()

    private val home = Library(
        id = "home",
        address = "https://books.home.lan:8443",
        name = "Home",
        username = "hoon",
        addedAt = 0,
        lastUsedAt = 0,
        isActive = true,
    )

    // TC-19
    @Test
    fun issue366_tc19_readsAsOneElement() {
        rule.setContent {
            OmnibusTheme { AccountCard(home) }
        }

        rule.onNodeWithTag(AccountCardTestTags.CARD)
            .assertContentDescriptionEquals("Home, signed in as hoon on books.home.lan:8443. Switch library")
        rule.onAllNodes(hasAnyAncestor(hasTestTag(AccountCardTestTags.CARD)))
            .assertCountEquals(0)
        rule.onAllNodesWithText("Home").assertCountEquals(0)
        rule.onAllNodesWithText("Switch").assertCountEquals(0)
    }

    // TC-19
    @Test
    fun issue366_tc19_readsAsOneElementWhenTheUsernameIsUnknown() {
        rule.setContent {
            OmnibusTheme { AccountCard(home.copy(username = null)) }
        }

        rule.onNodeWithTag(AccountCardTestTags.CARD)
            .assertContentDescriptionEquals("Home, on books.home.lan:8443. Switch library")
        rule.onAllNodes(hasAnyAncestor(hasTestTag(AccountCardTestTags.CARD)))
            .assertCountEquals(0)
    }

    // TC-20
    @Test
    fun issue366_tc20_youRootOrder() {
        val uiState = mutableStateOf(YouUIState(showsRequests = true, library = home))
        rule.setContent {
            OmnibusTheme {
                YouScreenContent(uiState.value, RequestWording(), onOpenRequests = {})
            }
        }

        val masthead = rule.onNodeWithText("You", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val card = rule.onNodeWithTag(AccountCardTestTags.CARD).getUnclippedBoundsInRoot()
        val requests = rule.onNodeWithTag(YouScreenTestTags.REQUESTS_ROW).getUnclippedBoundsInRoot()
        assertTrue("The masthead should sit above the card", masthead.bottom <= card.top)
        assertTrue("The card should sit above the Requests row", card.bottom <= requests.top)
        rule.onNodeWithText("Saving you a shelf").assertDoesNotExist()

        rule.runOnIdle { uiState.value = YouUIState(showsRequests = true, library = null) }

        rule.onNodeWithTag(AccountCardTestTags.CARD).assertDoesNotExist()
        rule.onNodeWithText("You", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(YouScreenTestTags.REQUESTS_ROW).assertExists()

        rule.runOnIdle { uiState.value = YouUIState(showsRequests = false, library = home) }

        rule.onNodeWithTag(AccountCardTestTags.CARD).assertExists()
        rule.onNodeWithText("You", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(YouScreenTestTags.REQUESTS_ROW).assertDoesNotExist()
    }

    // TC-9 (AC1, AC5)
    @Test
    fun issue367_tc9_tappingTheCardOpensTheSwitcher() {
        var opens = 0
        rule.setContent {
            OmnibusTheme {
                YouScreenContent(
                    YouUIState(showsRequests = false, library = home),
                    RequestWording(),
                    onOpenRequests = {},
                    onOpenSwitcher = { opens++ },
                )
            }
        }

        rule.onNodeWithTag(AccountCardTestTags.CARD)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        rule.runOnIdle { assertEquals(1, opens) }
    }
}
