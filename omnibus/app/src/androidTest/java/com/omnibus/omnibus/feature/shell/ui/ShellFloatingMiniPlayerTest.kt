package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerContent
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerFloatPlacement
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerFloatingPlacement
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerTestTags
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.shell.ShellNavigationLayout
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.OmnibusNavigationRail
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.components.bottomOverlay
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val playing = MiniPlayerUIState(
    visible = true,
    bookUuid = "book-1",
    cover = CoverIdentity(uuid = "book-1", title = "Plate", creator = "An Author", hasCover = true),
    title = "Mini Title",
    subtitle = "Chapter 7: The Question of Sex",
    progress = 0.35f,
    isPlaying = true,
)

/** Insets and the overlay's measured height, read in pixels from inside the shell's content. */
private class ShellInsets {
    var navigationBottom = 0
    var horizontalLeft = 0
    var horizontalRight = 0
    var overlayBottom = 0
}

/**
 * The floating mini player in the shell's bottom overlay, in both layouts (#271). The card comes from
 * [MiniPlayerFloatingPlacement] over the real [OmnibusTabBar] or [OmnibusNavigationRail], so the tests measure what the
 * shell draws. Geometry is asserted relative to the window, the rail and the tab bar, never to a fixed window size.
 */
class ShellFloatingMiniPlayerTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun items() = ShellTab.entries.map {
        OmnibusTabBarItem(context.getString(it.label), it.icon, it.selectedIcon)
    }

    // #271 TC-2 (R1, R7)
    @Test
    fun issue271_tc2_barCardIsInsetFromBothSides() {
        val insets = ShellInsets()
        setShell(ShellNavigationLayout.Bar, insets)

        val root = rule.onRoot().bounds()
        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        val twelve = with(rule.density) { 12.dp.toPx() }
        assertEquals("the card's left inset", root.left + insets.horizontalLeft + twelve, card.left, 1f)
        assertEquals("the card's right inset", root.right - insets.horizontalRight - twelve, card.right, 1f)
    }

    // #271 TC-2 (R1)
    @Test
    fun issue271_tc2_barCardSitsEightDpAboveTheTabBar() {
        setShell(ShellNavigationLayout.Bar, ShellInsets())

        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        val tabBar = rule.onNodeWithTag(TAB_BAR_TAG).bounds()
        val eight = with(rule.density) { 8.dp.toPx() }
        assertEquals("the gap between the card and the tab bar", tabBar.top, card.bottom + eight, 1f)
    }

    // #271 TC-3 (R2)
    @Test
    fun issue271_tc3_railCardIsCappedAtWidthOrContentLessMargins() {
        val insets = ShellInsets()
        setShell(ShellNavigationLayout.Rail, insets)

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val root = rule.onRoot().bounds()
        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        val density = rule.density
        val contentWidth = root.right - insets.horizontalRight - rail.right
        val expected = minOf(
            with(density) { 560.dp.toPx() },
            maxOf(0f, contentWidth - with(density) { 32.dp.toPx() }),
        )
        assertEquals("the card width", expected, card.width, 1f)
    }

    // #271 TC-3 (R2, R7)
    @Test
    fun issue271_tc3_railCardIsCentredInTheContentArea() {
        val insets = ShellInsets()
        setShell(ShellNavigationLayout.Rail, insets)

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val root = rule.onRoot().bounds()
        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        val contentRight = root.right - insets.horizontalRight
        assertEquals("the card centre", (rail.right + contentRight) / 2f, (card.left + card.right) / 2f, 1f)
    }

    // #271 TC-3 (R7)
    @Test
    fun issue271_tc3_railCardStaysClearOfTheRail() {
        setShell(ShellNavigationLayout.Rail, ShellInsets())

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        assertTrue("the card starts at ${card.left}, under the rail ending at ${rail.right}", card.left >= rail.right - 1f)
    }

    // #271 TC-3 (R2)
    @Test
    fun issue271_tc3_railCardSits16dpAboveTheNavigationBar() {
        val insets = ShellInsets()
        setShell(ShellNavigationLayout.Rail, insets)

        val root = rule.onRoot().bounds()
        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds()
        val sixteen = with(rule.density) { 16.dp.toPx() }
        assertEquals("the gap below the card", insets.navigationBottom + sixteen, root.bottom - card.bottom, 1f)
    }

    // #271 TC-4 (R6)
    @Test
    fun issue271_tc4a_barOverlayHeightIsCardGapPlusTabBar() {
        val insets = ShellInsets()
        setShell(ShellNavigationLayout.Bar, insets)

        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds().height
        val tabBar = rule.onNodeWithTag(TAB_BAR_TAG).bounds().height
        val eight = with(rule.density) { 8.dp.toPx() }
        assertEquals("the overlay inset", card + eight + tabBar, insets.overlayBottom.toFloat(), 1f)
    }

    // #271 TC-4 (R6)
    @Test
    fun issue271_tc4b_railOverlayHeightIsCardGapPlusNavigationBar() {
        val insets = ShellInsets()
        setShell(ShellNavigationLayout.Rail, insets)

        val card = rule.onNodeWithTag(MiniPlayerTestTags.BAR).bounds().height
        val sixteen = with(rule.density) { 16.dp.toPx() }
        assertEquals(
            "the overlay inset",
            card + sixteen + insets.navigationBottom,
            insets.overlayBottom.toFloat(),
            1f,
        )
    }

    // #271 TC-5 (R6, R8)
    @Test
    fun issue271_tc5_hiddenBarOverlayIsTabBar() {
        val insets = ShellInsets()
        setShell(ShellNavigationLayout.Bar, insets, shown = false)

        rule.onAllNodesWithTag(MiniPlayerTestTags.BAR).assertCountEquals(0)
        val tabBar = rule.onNodeWithTag(TAB_BAR_TAG).bounds().height
        assertEquals("the overlay inset", tabBar, insets.overlayBottom.toFloat(), 1f)
    }

    // #271 TC-6 (R5)
    @Test
    fun issue271_tc6_switchingLayoutKeepsTheMiniPlayerComposition() {
        val layout = mutableStateOf(ShellNavigationLayout.Bar)
        val placements = mutableListOf<MiniPlayerFloatPlacement>()
        val instances = mutableListOf<Any>()
        var starts = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellLayout(
                    overlay = {
                        ShellBottomOverlay(
                            navigationLayout = layout.value,
                            miniPlayer = { placement ->
                                placements += placement
                                instances += remember { Any() }
                                LaunchedEffect(Unit) { starts++ }
                                Box(Modifier.fillMaxWidth().height(64.dp).testTag(MINI_TAG))
                            },
                            tabBar = { Box(Modifier.fillMaxWidth().height(64.dp)) },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    navigationLayout = layout.value,
                    rail = { OmnibusNavigationRail(items = items(), selectedIndex = 0, onSelect = {}) },
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        rule.waitForIdle()

        rule.runOnIdle { layout.value = ShellNavigationLayout.Rail }
        rule.waitForIdle()
        rule.onAllNodesWithTag(MINI_TAG).assertCountEquals(1)
        rule.runOnIdle { layout.value = ShellNavigationLayout.Bar }
        rule.waitForIdle()
        rule.onAllNodesWithTag(MINI_TAG).assertCountEquals(1)

        assertEquals("the effect starts once", 1, starts)
        instances.forEach { assertSame(instances.first(), it) }
        assertEquals(MiniPlayerFloatPlacement.Bar, placements.first())
        assertTrue("the rail placement was used: $placements", MiniPlayerFloatPlacement.Rail in placements)
        assertEquals(MiniPlayerFloatPlacement.Bar, placements.last())
    }

    /**
     * Renders [ShellLayout] edge to edge with the floating card in its bottom overlay, over the real tab bar tagged
     * [TAB_BAR_TAG] or the rail tagged [RAIL_TAG]. [insets] receives the measured values from inside the content.
     */
    private fun setShell(layout: ShellNavigationLayout, insets: ShellInsets, shown: Boolean = true) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellLayout(
                    overlay = {
                        ShellBottomOverlay(
                            navigationLayout = layout,
                            miniPlayer = { placement ->
                                if (shown) {
                                    MiniPlayerFloatingPlacement(placement) {
                                        MiniPlayerContent(
                                            uiState = playing,
                                            onExpand = {},
                                            onSkipBack = {},
                                            onPlayPauseToggled = {},
                                            onStop = {},
                                            floating = true,
                                        )
                                    }
                                }
                            },
                            tabBar = {
                                Box(Modifier.testTag(TAB_BAR_TAG)) {
                                    OmnibusTabBar(items = items(), selectedIndex = 0, onSelect = {})
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    navigationLayout = layout,
                    rail = {
                        OmnibusNavigationRail(items = items(), selectedIndex = 0, onSelect = {}, modifier = Modifier.testTag(RAIL_TAG))
                    },
                ) {
                    val density = LocalDensity.current
                    val direction = LocalLayoutDirection.current
                    val horizontal = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    insets.navigationBottom = WindowInsets.navigationBars.getBottom(density)
                    insets.horizontalLeft = horizontal.getLeft(density, direction)
                    insets.horizontalRight = horizontal.getRight(density, direction)
                    insets.overlayBottom = WindowInsets.bottomOverlay.getBottom(density)
                    Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
                }
            }
        }
        rule.waitForIdle()
    }

    private companion object {
        const val RAIL_TAG = "shell-floating-rail"
        const val TAB_BAR_TAG = "shell-floating-tab-bar"
        const val CONTENT_TAG = "shell-floating-content"
        const val MINI_TAG = "shell-floating-slot"
    }
}
