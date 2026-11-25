package com.omnibus.omnibus.feature.libraries.manage

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryDetailScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val home = Library(
        id = "home",
        address = "https://books.home.lan",
        name = "Home",
        username = "hoon",
        addedAt = 1,
        lastUsedAt = 1,
        isActive = true,
    )

    private fun showDetail(
        state: LibraryDetailUIState = LibraryDetailUIState(library = home, name = "Home"),
        onNameChange: (String) -> Unit = {},
        onLeave: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                LibraryDetailScreenContent(
                    uiState = state,
                    onNameChange = onNameChange,
                    onLeave = onLeave,
                    onBack = onBack,
                )
            }
        }
    }

    private val nameIsEditable = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)
    private val noSetText = SemanticsMatcher.keyNotDefined(SemanticsActions.SetText)
    private val noEditableText = SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText)

    // TC-26 (R5, R13)
    @Test
    fun issue368_tc26_showsTheNameAddressAndUsernameLabelsAndTheHint() {
        showDetail()

        rule.onNodeWithText("Name").assertIsDisplayed()
        rule.onNodeWithText("Address").assertIsDisplayed()
        rule.onNodeWithText("Username").assertIsDisplayed()
        rule.onNodeWithText("Shown in your list of libraries. Leave it blank to use the address.").assertIsDisplayed()
    }

    @Test
    fun issue368_tc26_titleFollowsTheNameAndIsAHeading() {
        showDetail()

        rule.onNodeWithTag(LibraryDetailTestTags.TITLE).assertTextEquals("Home").assert(isHeading())
    }

    @Test
    fun issue368_tc26_theNameFieldIsEditable() {
        showDetail()

        rule.onNodeWithTag(LibraryDetailTestTags.NAME_FIELD).assert(nameIsEditable)
    }

    @Test
    fun issue368_tc26_theNameFieldReportsWhatIsTyped() {
        val changes = mutableListOf<String>()
        showDetail(onNameChange = { changes += it })

        rule.onNodeWithTag(LibraryDetailTestTags.NAME_FIELD).performTextReplacement("Den")

        rule.runOnIdle { assertEquals("Den", changes.last()) }
    }

    @Test
    fun issue368_tc26_addressIsOneReadOnlyNode() {
        showDetail()

        rule.onNodeWithTag(LibraryDetailTestTags.ADDRESS)
            .assertTextEquals("Address", "https://books.home.lan")
            .assert(noSetText)
            .assert(noEditableText)
    }

    @Test
    fun issue368_tc26_usernameIsOneReadOnlyNode() {
        showDetail()

        rule.onNodeWithTag(LibraryDetailTestTags.USERNAME)
            .assertTextEquals("Username", "hoon")
            .assert(noSetText)
            .assert(noEditableText)
    }

    // TC-27 (R5)
    @Test
    fun issue368_tc27_hidesTheUsernameWhileItIsUnknown() {
        showDetail(state = LibraryDetailUIState(library = home.copy(username = null), name = "Home"))

        rule.onNodeWithTag(LibraryDetailTestTags.USERNAME).assertDoesNotExist()
        rule.onNodeWithText("Username").assertDoesNotExist()
    }

    // TC-30 (R8)
    @Test
    fun issue368_tc30_theBackArrowLeavesAfterSavingAndNotBefore() {
        val calls = mutableListOf<String>()
        showDetail(onLeave = { calls += "leave" }, onBack = { calls += "back" })

        rule.onNodeWithContentDescription(rule.activity.getString(R.string.navigation_back)).performClick()

        rule.runOnIdle { assertEquals(listOf("leave", "back"), calls) }
    }

    // TC-32 (R8)
    @Test
    fun issue368_tc32_savesWhenTheAppStops() {
        val leaves = mutableListOf<String>()
        showDetail(onLeave = { leaves += "leave" })
        rule.waitForIdle()

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        rule.runOnIdle { assertEquals(listOf("leave"), leaves) }
    }

    @Test
    fun issue368_tc32_savesWhenTheContentLeavesComposition() {
        val leaves = mutableListOf<String>()
        val showing = mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                if (showing.value) {
                    LibraryDetailScreenContent(
                        uiState = LibraryDetailUIState(library = home, name = "Home"),
                        onNameChange = {},
                        onLeave = { leaves += "leave" },
                        onBack = {},
                    )
                }
            }
        }
        rule.waitForIdle()

        rule.runOnIdle { showing.value = false }

        rule.runOnIdle { assertEquals(listOf("leave"), leaves) }
    }

    // TC-34 (R12)
    @Test
    fun issue368_tc34_aMissingLibraryPopsOnce() {
        var backs = 0
        var leaves = 0
        showDetail(
            state = LibraryDetailUIState(isMissing = true),
            onLeave = { leaves++ },
            onBack = { backs++ },
        )

        rule.runOnIdle {
            assertEquals("The screen should leave once", 1, backs)
            assertEquals("A missing library saves nothing", 0, leaves)
        }
    }
}
