package com.omnibus.omnibus.feature.libraries.add

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.configuration.ServerConfigSubmissionStatus
import com.omnibus.omnibus.feature.configuration.ServerConfigUIState
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationContent
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Connect in its adding mode, and first-time setup beside it (#371). Relative asserts only. */
class AddLibraryConnectTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val addressField = "server-address"

    private val home = Library(
        id = "home",
        address = "http://books.home.lan",
        name = "Home",
        username = "hoon",
        addedAt = 1,
        lastUsedAt = 1,
        isActive = true,
    )

    @Test
    fun issue371_tc18_theCloseButtonIsLabelledAndTappable() {
        var closes = 0
        rule.setContent {
            OmnibusTheme {
                Surface {
                    ServerConfigurationContent(
                        modifier = Modifier,
                        uiState = ServerConfigUIState(savedConfigurationValue = "https://books.home.lan"),
                        isAdding = true,
                        onClose = { closes++ },
                        onConfigSubmitted = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(ServerConfigurationTestTags.CLOSE)
            .assertContentDescriptionEquals("Close")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals("the close button is tapped once", 1, closes)
    }

    @Test
    fun issue371_tc18_theCloseButtonSitsAboveTheAddressField() {
        rule.setContent {
            OmnibusTheme {
                Surface {
                    ServerConfigurationContent(
                        modifier = Modifier,
                        uiState = ServerConfigUIState(),
                        isAdding = true,
                        onConfigSubmitted = {},
                    )
                }
            }
        }

        val closeTop = rule.onNodeWithTag(ServerConfigurationTestTags.CLOSE).getUnclippedBoundsInRoot().top
        val fieldTop = rule.onNodeWithTag(addressField).getUnclippedBoundsInRoot().top
        assertTrue("the close button is above the address field", closeTop < fieldTop)
    }

    @Test
    fun issue371_tc18_theAddressFieldStartsEmpty() {
        rule.setContent {
            OmnibusTheme {
                Surface {
                    ServerConfigurationContent(
                        modifier = Modifier,
                        uiState = ServerConfigUIState(savedConfigurationValue = "https://books.home.lan"),
                        isAdding = true,
                        onConfigSubmitted = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(addressField)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun issue371_tc19_firstTimeSetupHasNoCloseButtonAndKeepsTheSavedAddress() {
        rule.setContent {
            OmnibusTheme {
                Surface {
                    ServerConfigurationContent(
                        modifier = Modifier,
                        uiState = ServerConfigUIState(savedConfigurationValue = "https://books.home.lan"),
                        onConfigSubmitted = {},
                    )
                }
            }
        }

        rule.onAllNodesWithTag(ServerConfigurationTestTags.CLOSE).assertCountEquals(0)
        rule.onNodeWithTag(addressField)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("https://books.home.lan")),
            )
    }

    @Test
    fun issue371_tc20_alreadySavedShowsTheLibraryAndOffersSwitchTo() {
        var switched: Library? = null
        rule.setContent {
            OmnibusTheme {
                Surface {
                    ServerConfigurationContent(
                        modifier = Modifier,
                        uiState = ServerConfigUIState(
                            submissionStatus = ServerConfigSubmissionStatus.ALREADY_SAVED,
                            alreadySaved = home,
                        ),
                        isAdding = true,
                        onSwitchTo = { switched = it },
                        onConfigSubmitted = {},
                    )
                }
            }
        }

        rule.onNodeWithText(context.getString(R.string.libraries_add_already_saved, home.address)).assertIsDisplayed()
        rule.onNodeWithTag(ServerConfigurationTestTags.SWITCH_TO)
            .assertTextEquals(context.getString(R.string.libraries_add_switch_to, home.name))
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        rule.onAllNodesWithText(context.getString(R.string.server_config_connect)).assertCountEquals(0)

        assertEquals("the saved library is switched to", home, switched)
    }
}
