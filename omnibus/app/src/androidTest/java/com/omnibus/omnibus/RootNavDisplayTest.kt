package com.omnibus.omnibus

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.auth.ui.LoginScreenRoute
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.audiobook.player.PlayerRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.reader.ui.ReaderRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// TC-58 to TC-62 and TC-82. The shell, reader and sign-in screens are fakes with test tags, so the
// navigation is what's under test. The real BookDetailScreenContent is used only for TC-62 (#93 TC-126).
class RootNavDisplayTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val stack = mutableStateListOf<NavKey>(SignedInShellRoute)
    private val isSignedIn = mutableStateOf<Boolean?>(true)

    private fun setRoot(shell: @Composable () -> Unit = { TestShell() }) {
        rule.setContent {
            OmnibusTheme {
                RootNavDisplay(
                    backstack = stack,
                    isSignedIn = isSignedIn.value,
                    entryProvider = entryProvider {
                        entry<SignedInShellRoute> { shell() }
                        entry<ReaderRoute> { key ->
                            Box(Modifier.fillMaxSize().testTag(FAKE_READER)) {
                                Text(key.bookUuid)
                            }
                        }
                        entry<PlayerRoute> {
                            Box(Modifier.fillMaxSize().testTag(FAKE_PLAYER))
                        }
                        entry<LoginScreenRoute> {
                            Text("fake login", modifier = Modifier.testTag(FAKE_LOGIN))
                        }
                    },
                )
            }
        }
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    // TC-59
    @Test
    fun tc59_leavingTheReaderRestoresTheShellState() {
        setRoot()
        rule.onNodeWithTag(COUNTER).performClick().performClick()
        rule.onNodeWithText("count: 2").assertIsDisplayed()

        rule.runOnIdle { stack.openReader("book-1") }
        rule.waitForIdle()
        pressBack()
        rule.waitForIdle()

        rule.onNodeWithText("count: 2").assertIsDisplayed()
        assertEquals(listOf<NavKey>(SignedInShellRoute), stack.toList())
    }

    // #93 TC-126
    @Test
    fun tc62_theDetailReadButtonOpensTheReaderForThatBook() {
        val book = Book(
            id = 1,
            uniqueIdentifier = "book-1",
            filename = "book-1.epub",
            title = "Book One",
            creators = emptyList(),
            coverUrl = null,
            accent = null,
            formats = listOf("epub"),
            series = null,
            seriesIndex = null,
            hasPhysical = false,
            addedAt = null,
            lastInteractedAt = null,
            bookFiles = emptyList(),
        )
        setRoot(
            shell = {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = book, isLoading = false),
                    onNavigate = { if (it is BookDetailDestination.Reader) stack.openReader(it.bookUuid) },
                    onClose = {},
                    onRetry = {},
                    onRefresh = {},
                    onReloadBook = {},
                    onBookChanged = {},
                    onTransientErrorShown = {},
                    slots = com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots(),
                )
            },
        )

        rule.onNodeWithText("Read").performClick()
        rule.waitForIdle()

        assertEquals(listOf<NavKey>(SignedInShellRoute, ReaderRoute("book-1")), stack.toList())
        rule.onNodeWithText("book-1").assertIsDisplayed()
    }

    // #165 TC-9 (P-SCR-AC1, D1)
    @Test
    fun playerRouteCoversTheShellAndItsTabBar() {
        setRoot()
        rule.onNodeWithTag(FAKE_TAB_BAR).assertIsDisplayed()

        rule.runOnIdle { stack.openPlayer("book-1", 9L) }
        rule.waitForIdle()

        rule.onAllNodesWithTag(FAKE_TAB_BAR).assertCountEquals(0)
        rule.onNodeWithTag(FAKE_PLAYER).assertIsDisplayed()

        pressBack()
        rule.waitForIdle()

        rule.onNodeWithTag(FAKE_TAB_BAR).assertIsDisplayed()
        assertEquals(listOf<NavKey>(SignedInShellRoute), stack.toList())
    }

    // TC-82
    @Test
    fun tc82_signOutWithTheReaderOpenGoesToLogin() {
        setRoot()
        rule.runOnIdle { stack.openReader("book-1") }
        rule.waitForIdle()

        rule.runOnIdle { isSignedIn.value = false }
        rule.waitForIdle()

        rule.onNodeWithTag(FAKE_LOGIN).assertIsDisplayed()
        assertEquals(listOf<NavKey>(LoginScreenRoute), stack.toList())
    }

    /** The shell stand-in: a counter that survives leaving the reader, and a tab bar node. */
    @Composable
    private fun TestShell() {
        val count = rememberSaveable { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "count: ${count.intValue}",
                modifier = Modifier.testTag(COUNTER).clickable { count.intValue++ },
            )
            Box(Modifier.fillMaxWidth().height(48.dp).testTag(FAKE_TAB_BAR))
        }
    }

    private companion object {
        const val COUNTER = "fake_counter"
        const val FAKE_TAB_BAR = "fake_tab_bar"
        const val FAKE_READER = "fake_reader"
        const val FAKE_PLAYER = "fake_player"
        const val FAKE_LOGIN = "fake_login"
    }
}
