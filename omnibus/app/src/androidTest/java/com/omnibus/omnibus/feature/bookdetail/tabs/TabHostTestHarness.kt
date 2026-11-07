package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The context a [TabHostUnderTest] exposes to its test. */
internal class TabHostHandle {
    lateinit var context: BookDetailSectionContext
}

/**
 * The tab host alone, on a plain scroll column, with a pin at the top of the window (pinTop 0). Tests use it through
 * [setTabHost].
 */
@Composable
internal fun TabHostUnderTest(
    handle: TabHostHandle,
    slots: BookDetailSlots,
    reducedMotion: Boolean,
) {
    val scroll = rememberScrollState()
    val state = rememberBookDetailTabController(scroll, reducedMotion)
    val context = remember(state) {
        val book = testDetailBook()
        testSectionContext(
            book = book,
            tabs = state,
            accent = bookAccent(book.coverIdentity().tone, lightGround = false),
        )
    }
    handle.context = context
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        // Puts the strip below the pin, so it rests unpinned with its ribbon out.
        Spacer(Modifier.height(48.dp))
        BookDetailTabHost(
            state = state,
            context = context,
            slots = slots,
            scrollState = scroll,
            pinTop = 0.dp,
            minHeight = 0.dp,
            reducedMotion = reducedMotion,
        )
    }
}

/** Sets [TabHostUnderTest] in dark theme and waits for its first composition. */
internal fun ComposeContentTestRule.setTabHost(
    slots: BookDetailSlots = BookDetailSlots(),
    reducedMotion: Boolean = true,
): TabHostHandle {
    val handle = TabHostHandle()
    setContent {
        OmnibusTheme(darkTheme = true) {
            TabHostUnderTest(handle, slots, reducedMotion)
        }
    }
    waitForIdle()
    return handle
}

/** A short panel section that shows [tag] as text. */
fun fakeSection(tag: String): BookDetailSection = BookDetailSection { _, modifier ->
    Text(text = tag, modifier = modifier.testTag(tag))
}

/** A panel section four times as tall as the window is wide, so a page scrolls far past the strip. */
fun tallFakeSection(tag: String, color: Color = Color.Unspecified): BookDetailSection = BookDetailSection { _, modifier ->
    Box(modifier.fillMaxWidth().aspectRatio(0.25f).background(color).testTag(tag))
}

fun ComposeContentTestRule.tabNode(tab: BookDetailTab): SemanticsNodeInteraction =
    onNodeWithTag(BookDetailTabsTestTags.tab(tab))

fun ComposeContentTestRule.ribbonNode(): SemanticsNodeInteraction =
    onNodeWithTag(BookDetailTabsTestTags.RIBBON, useUnmergedTree = true)

/** Fails unless the node is in the tree. Not required to be on screen. */
fun SemanticsNodeInteraction.assertExists(): SemanticsNodeInteraction = apply {
    fetchSemanticsNode("node does not exist")
}

/** Taps the tab in the strip and waits for the page to settle. */
fun ComposeContentTestRule.tapTab(tab: BookDetailTab) {
    tabNode(tab).performClick()
    waitForIdle()
}

