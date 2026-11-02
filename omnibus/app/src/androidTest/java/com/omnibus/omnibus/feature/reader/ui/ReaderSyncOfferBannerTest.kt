package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.feature.reader.ReaderStart
import com.omnibus.omnibus.feature.reader.SyncOffer
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val UNDERLAY_TAG = "reader_sync_offer_test_underlay"
private const val ROOT_TAG = "reader_sync_offer_test_root"

/** Matches the copy in strings.xml, so the test reads the real text. */
private const val TITLE = "Continue from another device?"
private const val DETAIL = "Another device left off further along."

/** A mid-book location, so the bottom label shows. */
private val BANNER_LOCATION = RelocateData(page = 361, totalPages = 661, pct = 54, chapter = 3, totalChapters = 12, chapterPagesLeft = 7)

// TC-108 to TC-113. Tests use tags, text and relative bounds only, and never pin a window size, so they
// hold on the 320dp CI AVD and on a phone-sized one.
class ReaderSyncOfferBannerTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A page loader that never opens a book: the reader's page is a plain ground under inspection. */
    private val loader = ReaderAssetLoaderFactory(
        context,
        Dispatchers.Unconfined,
        BookFileSource { BookFileResult.NotFound },
    )

    private val controller = ReaderController()

    /** The banner with the reader's real copy and colours for [theme]. */
    @Composable
    private fun Banner(
        theme: ReaderTheme,
        detail: String = DETAIL,
        onAccept: () -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        ReaderSyncOfferBanner(
            title = stringResource(R.string.reader_sync_offer_title),
            detail = detail,
            goLabel = stringResource(R.string.reader_sync_offer_go),
            dismissLabel = stringResource(R.string.reader_sync_offer_dismiss),
            colors = readerChromeColors(theme),
            actionColor = readerSyncOfferActionColor(theme),
            onAccept = onAccept,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }

    /** The reader screen in inspection mode (no WebView), reading [uiState] on every composition. */
    private fun showScreen(uiState: () -> ReaderUIState) {
        rule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                OmnibusTheme {
                    ReaderScreenContent(
                        uiState = uiState(),
                        bookUuid = "book-1",
                        controller = controller,
                        assetLoaderFactory = loader,
                        commands = emptyFlow(),
                        onPageEvent = {},
                        onSheetDismissed = {},
                        onMenuOpened = {},
                        onMenuDismissed = {},
                        onMenuRowChosen = {},
                        onRetryBookRequested = {},
                        onReaderClosed = {},
                        onReaderStopped = {},
                        onSyncOfferAccepted = {},
                        onSyncOfferDismissed = {},
                        onClose = {},
                    )
                }
            }
        }
    }

    private val offer = SyncOffer(ReaderStart.AtCfi("epubcfi(/6/10)"))

    private fun readyState(
        syncOffer: SyncOffer? = offer,
        chromeVisible: Boolean = false,
    ) = ReaderUIState(
        book = ReaderBookState.Ready(title = "Nineteen Eighty-Four"),
        loadStatus = ReaderLoadStatus.Ready,
        location = BANNER_LOCATION,
        menuRows = listOf(ReaderMenuRow.Contents),
        chromeVisible = chromeVisible,
        syncOffer = syncOffer,
    )

    // TC-108
    @Test
    fun tc108_bannerShowsCopyAndActions() {
        var accepted = 0
        var dismissed = 0
        rule.setContent {
            OmnibusTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    Banner(
                        theme = ReaderTheme.Light,
                        onAccept = { accepted++ },
                        onDismiss = { dismissed++ },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        rule.onNodeWithText(TITLE).assertIsDisplayed()
        rule.onNodeWithText(DETAIL).assertIsDisplayed()
        rule.onNodeWithText("Go").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dismiss").assertIsDisplayed()

        rule.onNodeWithText("Go").performClick()
        rule.onNodeWithContentDescription("Dismiss").performClick()
        rule.runOnIdle {
            assertEquals(1, accepted)
            assertEquals(1, dismissed)
        }
    }

    // TC-109
    @Test
    fun tc109_bannerNeedsReadyAndAnOffer() {
        val state = mutableStateOf(readyState().copy(loadStatus = ReaderLoadStatus.Loading))
        showScreen { state.value }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReaderSyncOfferTags.BANNER).assertCountEquals(0)

        state.value = state.value.copy(loadStatus = ReaderLoadStatus.Failed(ReaderLoadFailure.Render))
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReaderSyncOfferTags.BANNER).assertCountEquals(0)

        state.value = state.value.copy(loadStatus = ReaderLoadStatus.Ready)
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderSyncOfferTags.BANNER).assertIsDisplayed()

        state.value = state.value.copy(syncOffer = null)
        rule.waitForIdle()
        rule.onAllNodesWithTag(ReaderSyncOfferTags.BANNER).assertCountEquals(0)
    }

    // TC-111
    @Test
    fun tc111_bannerIsAnchoredAboveTheBottomBandWhateverTheChrome() {
        val state = mutableStateOf(readyState(chromeVisible = false))
        showScreen { state.value }
        rule.waitForIdle()

        val root = rule.onNodeWithTag(ReaderScreenTags.ROOT).getUnclippedBoundsInRoot()
        val hiddenBanner = rule.onNodeWithTag(ReaderSyncOfferTags.BANNER).getUnclippedBoundsInRoot()
        val bottomLabel = rule.onNodeWithTag(ReaderChromeTags.LABEL_BOTTOM).getUnclippedBoundsInRoot()
        val rootMiddle = root.top + (root.bottom - root.top) / 2

        assertTrue("banner must sit above the bottom label", hiddenBanner.bottom <= bottomLabel.top)
        assertTrue("banner must sit in the lower half", hiddenBanner.bottom > rootMiddle)
        assertTrue("banner must sit inside the window horizontally", hiddenBanner.left >= root.left && hiddenBanner.right <= root.right)

        state.value = state.value.copy(chromeVisible = true)
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderChromeTags.CLOSE).assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeTags.MENU).assertIsDisplayed()

        val shownBanner = rule.onNodeWithTag(ReaderSyncOfferTags.BANNER).getUnclippedBoundsInRoot()
        val close = rule.onNodeWithTag(ReaderChromeTags.CLOSE).getUnclippedBoundsInRoot()
        val menu = rule.onNodeWithTag(ReaderChromeTags.MENU).getUnclippedBoundsInRoot()
        assertTrue("banner must not overlap the close button", !shownBanner.overlaps(close))
        assertTrue("banner must not overlap the menu button", !shownBanner.overlaps(menu))
        assertEquals("the banner must not move when the chrome toggles", hiddenBanner, shownBanner)
    }

    // TC-112
    @Test
    fun tc112_bannerDoesNotInterceptTouchesOutsideItsBounds() {
        var underlayTaps = 0
        var accepted = 0
        var dismissed = 0
        rule.setContent {
            OmnibusTheme(darkTheme = false) {
                // As on the reader screen, the banner is an overlay sibling of the page, not its child.
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag(UNDERLAY_TAG)
                            .clickable { underlayTaps++ },
                    )
                    Banner(
                        theme = ReaderTheme.Light,
                        onAccept = { accepted++ },
                        onDismiss = { dismissed++ },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        rule.onNodeWithTag(UNDERLAY_TAG).performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(1, underlayTaps) }

        // A tap on the banner's own title stays with the banner.
        rule.onNodeWithText(TITLE).performTouchInput { click() }
        rule.runOnIdle {
            assertEquals(1, underlayTaps)
            assertEquals(0, accepted)
            assertEquals(0, dismissed)
        }
    }

    // TC-113
    @Test
    fun tc113_longDetailIsCappedAtTwoLines() {
        val longDetail = "$DETAIL ".repeat(11).trim()
        rule.setContent {
            OmnibusTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().testTag(ROOT_TAG)) {
                    Banner(
                        theme = ReaderTheme.Light,
                        detail = longDetail,
                        onAccept = {},
                        onDismiss = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = Spacing.md)
                            .fillMaxWidth(),
                    )
                }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(ReaderSyncOfferTags.DETAIL).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult]
            .action?.invoke(layouts)
        val layout = layouts.single()
        assertTrue("detail is capped at two lines, was ${layout.lineCount}", layout.lineCount <= 2)
        assertTrue("the capped detail is ellipsized", layout.hasVisualOverflow)

        rule.onNodeWithText("Go").assertIsDisplayed()
        rule.onNodeWithContentDescription("Dismiss").assertIsDisplayed()

        val root = rule.onNodeWithTag(ROOT_TAG).getUnclippedBoundsInRoot()
        val banner = rule.onNodeWithTag(ReaderSyncOfferTags.BANNER).getUnclippedBoundsInRoot()
        assertTrue("banner stays within the window width", banner.left >= root.left && banner.right <= root.right)
    }
}

/** Whether this rect and [other] share any area. */
private fun DpRect.overlaps(other: DpRect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom
