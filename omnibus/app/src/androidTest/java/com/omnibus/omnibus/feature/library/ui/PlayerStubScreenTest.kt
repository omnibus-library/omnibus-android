package com.omnibus.omnibus.feature.library.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class PlayerStubScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue22_tc38_showsTheUuidAndTheSavedFileId() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerStubScreen(bookUuid = "7f3c", bookFileId = 7L)
            }
        }

        rule.onNodeWithText("Player").assertIsDisplayed()
        rule.onNodeWithText("uuid: 7f3c").assertIsDisplayed()
        rule.onNodeWithText("file id: 7").assertIsDisplayed()
    }

    @Test
    fun issue22_tc38_showsNoneWithoutAFileId() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerStubScreen(bookUuid = "7f3c", bookFileId = null)
            }
        }

        rule.onNodeWithText("file id: none").assertIsDisplayed()
    }

    // TC-54
    @Test
    fun issue117_tc54_showsTheRequestedPosition() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerStubScreen(bookUuid = "7f3c", bookFileId = null, positionSeconds = 4332.5)
            }
        }

        rule.onNodeWithText("at: 4332.5").assertIsDisplayed()
    }

    // TC-55
    @Test
    fun issue117_tc55_omitsThePositionLineWhenNull() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                PlayerStubScreen(bookUuid = "7f3c", bookFileId = null)
            }
        }

        rule.onAllNodesWithText("at: ", substring = true).assertCountEquals(0)
    }
}
