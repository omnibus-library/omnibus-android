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
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.shelves.ui.ShelvesRoute
import com.omnibus.omnibus.feature.shell.LibraryRoot
import com.omnibus.omnibus.feature.shell.ShellNavigationLayout
import com.omnibus.omnibus.feature.shell.ShellNavigator
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.components.OmnibusNavigationRail
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.components.bottomOverlay
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.compose.foundation.layout.statusBars
import com.omnibus.omnibus.feature.shelves.ShelvesUIState
import com.omnibus.omnibus.feature.shelves.ui.ShelvesScreenContent
import com.omnibus.omnibus.feature.shelves.ui.ShelvesScreenTestTags

class ShellLayoutRailTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun items() = ShellTab.entries.map {
        OmnibusTabBarItem(context.getString(it.label), it.icon, it.selectedIcon)
    }

    // TC-6
    @Test
    fun tc6a_railSpansHeightBesideContent() {
        setShell(ShellNavigationLayout.Rail)

        val root = rule.onRoot().bounds()
        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val content = rule.onNodeWithTag(CONTENT_TAG).bounds()
        assertEquals(root.height, rail.height, 1f)
        assertTrue("content starts at ${content.left}, under the rail ending at ${rail.right}", content.left >= rail.right - 1f)
    }

    // TC-6
    @Test
    fun tc6b_overlaySitsBesideRail() {
        setShell(ShellNavigationLayout.Rail)

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val overlay = rule.onNodeWithTag(OVERLAY_TAG).bounds()
        assertTrue("overlay starts at ${overlay.left}, under the rail ending at ${rail.right}", overlay.left >= rail.right - 1f)
    }

    // TC-6
    @Test
    fun tc6c_barModeDrawsNoRail() {
        setShell(ShellNavigationLayout.Bar)

        rule.onAllNodesWithTag(RAIL_TAG).assertCountEquals(0)
    }

    // TC-6
    @Test
    fun tc6d_railConsumesStartInsets() {
        var rawLeft = 0
        var consumedLeft = 0
        setShell(
            ShellNavigationLayout.Rail,
            content = {
                val density = LocalDensity.current
                rawLeft = WindowInsets.systemBars.union(WindowInsets.displayCutout).getLeft(density, LayoutDirection.Ltr)
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(CONTENT_TAG)
                        .onConsumedWindowInsetsChanged { consumedLeft = it.getLeft(density, LayoutDirection.Ltr) },
                )
            },
        )

        assertEquals(rawLeft, consumedLeft)
    }

    // TC-6
    @Test
    fun tc6e_barModeConsumesNoStartInsets() {
        var consumedLeft = 0
        setShell(
            ShellNavigationLayout.Bar,
            content = {
                val density = LocalDensity.current
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(CONTENT_TAG)
                        .onConsumedWindowInsetsChanged { consumedLeft = it.getLeft(density, LayoutDirection.Ltr) },
                )
            },
        )

        assertEquals(0, consumedLeft)
    }

    // TC-7
    @Test
    fun tc7a_hiddenMiniPlayerStillClearsNavigationBar() {
        val miniPlayerShown = false
        var navigationBottom = 0
        var overlayBottom = 0
        setShell(
            ShellNavigationLayout.Rail,
            overlay = {
                ShellRailOverlay { insets ->
                    if (miniPlayerShown) Box(Modifier.fillMaxWidth().testTag(MINI_TAG).windowInsetsPadding(insets).height(64.dp))
                }
            },
            content = {
                val density = LocalDensity.current
                navigationBottom = WindowInsets.navigationBars.getBottom(density)
                overlayBottom = WindowInsets.bottomOverlay.getBottom(density)
                Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
            },
        )

        assertEquals(navigationBottom.toFloat(), overlayBottom.toFloat(), 1f)
    }

    // TC-7
    @Test
    fun tc7b_bottomOverlayIsMiniPlayerHeight() {
        val miniPlayerShown = true
        var navigationBottom = 0
        var overlayBottom = 0
        var sixtyThreeDpPx = 0f
        setShell(
            ShellNavigationLayout.Rail,
            overlay = {
                ShellRailOverlay { insets ->
                    if (miniPlayerShown) Box(Modifier.fillMaxWidth().testTag(MINI_TAG).windowInsetsPadding(insets).height(64.dp))
                }
            },
            content = {
                val density = LocalDensity.current
                navigationBottom = WindowInsets.navigationBars.getBottom(density)
                overlayBottom = WindowInsets.bottomOverlay.getBottom(density)
                sixtyThreeDpPx = with(density) { 63.dp.toPx() }
                Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
            },
        )

        val miniPlayer = rule.onNodeWithTag(MINI_TAG).bounds()
        assertEquals(miniPlayer.height, overlayBottom.toFloat(), 1f)
        assertTrue(
            "the mini player is ${miniPlayer.height}px, not taller than the navigation bar $navigationBottom + 63dp",
            miniPlayer.height > navigationBottom + sixtyThreeDpPx,
        )
    }

    // TC-9
    @Test
    fun tc9_switchingLayoutKeepsContentComposition() {
        val layout = mutableStateOf(ShellNavigationLayout.Bar)
        val instances = mutableListOf<Any>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellLayout(
                    overlay = { Box(Modifier.fillMaxWidth().height(64.dp)) },
                    modifier = Modifier.fillMaxSize(),
                    navigationLayout = layout.value,
                    rail = { OmnibusNavigationRail(items = items(), selectedIndex = 0, onSelect = {}, modifier = Modifier.testTag(RAIL_TAG)) },
                ) {
                    instances += remember { Any() }
                    Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
                }
            }
        }
        rule.waitForIdle()

        rule.runOnIdle { layout.value = ShellNavigationLayout.Rail }
        rule.waitForIdle()
        rule.onAllNodesWithTag(RAIL_TAG).assertCountEquals(1)
        rule.runOnIdle { layout.value = ShellNavigationLayout.Bar }
        rule.waitForIdle()
        rule.onAllNodesWithTag(RAIL_TAG).assertCountEquals(0)

        assertTrue(instances.isNotEmpty())
        instances.forEach { assertSame(instances.first(), it) }
    }

    // TC-10
    @Test
    fun tc10_selectionAndStacksSurviveRecreationAcrossLayouts() {
        val tester = StateRestorationTester(rule)
        val layout = mutableStateOf(ShellNavigationLayout.Bar)
        var navigator: ShellNavigator? = null
        tester.setContent {
            val shellNavigator = rememberShellNavigator()
            navigator = shellNavigator
            OmnibusTheme(darkTheme = true) {
                ShellLayout(
                    overlay = { Box(Modifier.fillMaxWidth().height(64.dp)) },
                    modifier = Modifier.fillMaxSize(),
                    navigationLayout = layout.value,
                    rail = {
                        OmnibusNavigationRail(
                            items = items(),
                            selectedIndex = ShellTab.entries.indexOf(shellNavigator.selected),
                            onSelect = { shellNavigator.select(ShellTab.entries[it]) },
                        )
                    },
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        rule.runOnIdle {
            navigator!!.select(ShellTab.Library)
            navigator!!.navigateTo(ShelvesRoute)
            navigator!!.select(ShellTab.Stats)
        }
        rule.runOnIdle { layout.value = ShellNavigationLayout.Rail }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        val restored = navigator!!
        assertEquals(ShellTab.Stats, restored.selected)
        rule.runOnIdle { restored.select(ShellTab.Library) }
        rule.waitForIdle()
        assertEquals(listOf(LibraryRoot, ShelvesRoute), restored.backStack.takeLast(2))
    }

    // TC-18
    @Test
    fun tc18_railLeadsOnTheRightInRtl() {
        setShell(ShellNavigationLayout.Rail, direction = LayoutDirection.Rtl)

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val content = rule.onNodeWithTag(CONTENT_TAG).bounds()
        assertTrue("rail starts at ${rail.left}, left of the content ending at ${content.right}", rail.left >= content.right - 1f)
    }

    // TC-24
    @Test
    fun issue270_tc24a_shelvesBarUnderStatusBar() {
        var statusTop = 0
        setShell(
            ShellNavigationLayout.Bar,
            content = {
                statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)
                ShellUnderStatusBarEntry {
                    ShelvesScreenContent(
                        uiState = ShelvesUIState(),
                        onBack = {},
                        onRefresh = {},
                        onRetry = {},
                        onTransientErrorShown = {},
                    )
                }
            },
        )

        assertShelvesBarUnderStatusBar(statusTop)
    }

    // TC-24
    @Test
    fun issue270_tc24b_shelvesBarBesideRail() {
        var statusTop = 0
        setShell(
            ShellNavigationLayout.Rail,
            content = {
                statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)
                ShellUnderStatusBarEntry {
                    ShelvesScreenContent(
                        uiState = ShelvesUIState(),
                        onBack = {},
                        onRefresh = {},
                        onRetry = {},
                        onTransientErrorShown = {},
                    )
                }
            },
        )

        assertShelvesBarUnderStatusBar(statusTop)
        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val bar = rule.onNodeWithTag(ShelvesScreenTestTags.TOP_BAR).bounds()
        val back = rule.onNodeWithTag(ShelvesScreenTestTags.BACK).bounds()
        assertTrue("the bar starts at ${bar.left}, under the rail ending at ${rail.right}", bar.left >= rail.right - 1f)
        assertTrue("the back arrow sits ${back.left - bar.left}px in, a second rail inset", back.left - bar.left < back.width)
    }

    private fun assertShelvesBarUnderStatusBar(statusTop: Int) {
        val root = rule.onRoot().bounds()
        val bar = rule.onNodeWithTag(ShelvesScreenTestTags.TOP_BAR).bounds()
        val back = rule.onNodeWithTag(ShelvesScreenTestTags.BACK).bounds()
        assertEquals(root.top, bar.top, 1f)
        assertTrue("the back arrow starts at ${back.top}, inside the status bar of $statusTop", back.top >= statusTop - 1f)
    }

    /**
     * Renders [ShellLayout] edge to edge, with a 64dp overlay, the [OmnibusNavigationRail] tagged [RAIL_TAG] and a
     * full-size content box tagged [CONTENT_TAG] unless the test supplies its own.
     */
    private fun setShell(
        layout: ShellNavigationLayout,
        direction: LayoutDirection = LayoutDirection.Ltr,
        overlay: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().height(64.dp).testTag(OVERLAY_TAG)) },
        content: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag(CONTENT_TAG)) },
    ) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                OmnibusTheme(darkTheme = true) {
                    ShellLayout(
                        overlay = overlay,
                        modifier = Modifier.fillMaxSize(),
                        navigationLayout = layout,
                        rail = {
                            OmnibusNavigationRail(items = items(), selectedIndex = 0, onSelect = {}, modifier = Modifier.testTag(RAIL_TAG))
                        },
                        content = content,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private companion object {
        const val RAIL_TAG = "shell-rail"
        const val CONTENT_TAG = "shell-content"
        const val OVERLAY_TAG = "shell-overlay"
        const val MINI_TAG = "shell-mini-player"
    }
}
