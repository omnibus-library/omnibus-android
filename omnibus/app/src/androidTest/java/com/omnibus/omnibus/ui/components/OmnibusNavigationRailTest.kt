package com.omnibus.omnibus.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OmnibusNavigationRailTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun items() = ShellTab.entries.map {
        OmnibusTabBarItem(context.getString(it.label), it.icon, it.selectedIcon)
    }

    // TC-11
    @Test
    fun tc11a_firstTabClearsTheStatusBar() {
        val insets = renderEdgeToEdgeRail()

        val firstTab = rule.onAllNodes(TAB)[0].bounds()
        assertTrue("first tab top ${firstTab.top} is above the status bar ${insets.statusTopPx}", firstTab.top >= insets.statusTopPx)
    }

    // TC-11
    @Test
    fun tc11b_railIsEightyDpPlusTheStartInset() {
        val insets = renderEdgeToEdgeRail()

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        assertEquals(insets.railWidthPx + insets.startInsetPx, rail.width, 1f)
    }

    // TC-11
    @Test
    fun tc11c_everyTabStaysInsideTheEightyDpColumn() {
        val insets = renderEdgeToEdgeRail()

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val columnLeft = rail.left + insets.startInsetPx
        val columnRight = columnLeft + insets.railWidthPx
        rule.onAllNodes(TAB).fetchSemanticsNodes().forEach { tab ->
            val bounds = tab.boundsInRoot
            assertTrue("tab ${bounds.left}..${bounds.right} starts left of the column $columnLeft", bounds.left >= columnLeft - 1f)
            assertTrue("tab ${bounds.left}..${bounds.right} ends right of the column $columnRight", bounds.right <= columnRight + 1f)
        }
    }

    // TC-11
    @Test
    fun tc11d_tabsAreCenteredBetweenTheSystemBars() {
        val insets = renderEdgeToEdgeRail()

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val tabs = rule.onAllNodes(TAB).fetchSemanticsNodes().map { it.boundsInRoot }
        val gapAbove = tabs.first().top - insets.statusTopPx
        val gapBelow = (rail.bottom - insets.bottomInsetPx) - tabs.last().bottom
        assertEquals("gap above the tabs $gapAbove, below $gapBelow", gapAbove, gapBelow, 2f)
    }

    // TC-12
    @Test
    fun tc12_exposesTabsInAGroup() {
        val labels = ShellTab.entries.map { context.getString(it.label) }
        val searchIndex = ShellTab.entries.indexOf(ShellTab.Search)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                OmnibusNavigationRail(items = items(), selectedIndex = searchIndex, onSelect = {})
            }
        }

        rule.onAllNodes(TAB).assertCountEquals(ShellTab.entries.size)
        rule.onAllNodes(TAB and hasParent(SELECTABLE_GROUP)).assertCountEquals(ShellTab.entries.size)
        rule.onAllNodes(TAB and isSelected()).assertCountEquals(1)
        rule.onNode(TAB and isSelected()).assertTextEquals(labels[searchIndex])
        val shownLabels = rule.onAllNodes(TAB).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.Text].single().text }
        assertEquals(labels, shownLabels)
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    // TC-13
    @Test
    fun tc13_targetsAreAtLeast48dp() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                OmnibusNavigationRail(items = items(), selectedIndex = 0, onSelect = {})
            }
        }

        rule.onAllNodes(TAB).assertCountEquals(ShellTab.entries.size)
        for (index in ShellTab.entries.indices) {
            rule.onAllNodes(TAB)[index]
                .assertWidthIsEqualTo(80.dp)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    // TC-14
    @Test
    fun tc14a_reTapReportsSelectionWithoutHaptic() {
        val selections = mutableListOf<Int>()
        val haptics = RecordingHaptics()
        val searchIndex = ShellTab.entries.indexOf(ShellTab.Search)
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OmnibusTheme(darkTheme = true) {
                    OmnibusNavigationRail(items = items(), selectedIndex = searchIndex, onSelect = { selections += it })
                }
            }
        }

        rule.onNode(TAB and hasText(context.getString(ShellTab.Search.label))).performClick()

        assertEquals(listOf(searchIndex), selections)
        assertEquals(0, haptics.count)
    }

    // TC-14
    @Test
    fun tc14b_newSelectionTicksOnce() {
        val selections = mutableListOf<Int>()
        val haptics = RecordingHaptics()
        val statsIndex = ShellTab.entries.indexOf(ShellTab.Stats)
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OmnibusTheme(darkTheme = true) {
                    OmnibusNavigationRail(items = items(), selectedIndex = 0, onSelect = { selections += it })
                }
            }
        }

        rule.onNode(TAB and hasText(context.getString(ShellTab.Stats.label))).performClick()

        assertEquals(listOf(statsIndex), selections)
        assertEquals(1, haptics.count)
    }

    // #286 TC-9a (R8, R11)
    @Test
    fun issue286_tc9a_footerSitsBelowTheCentredTabs() {
        val insets = renderEdgeToEdgeRail(footer = { Box(Modifier.fillMaxWidth().height(48.dp).testTag(FOOTER_TAG)) })

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val footerBounds = rule.onNodeWithTag(FOOTER_TAG).bounds()
        val tabs = rule.onAllNodes(TAB).fetchSemanticsNodes().map { it.boundsInRoot }
        val hairline = with(rule.density) { 0.5.dp.roundToPx().coerceAtLeast(1) }
        val groupTop = tabs.minOf { it.top }
        val groupBottom = tabs.maxOf { it.bottom }
        assertTrue("footer top ${footerBounds.top} is above the last tab's bottom $groupBottom", footerBounds.top >= groupBottom)
        assertEquals("footer bottom", rail.bottom - insets.bottomInsetPx, footerBounds.bottom, 1f)
        val gapAbove = groupTop - insets.statusTopPx
        val gapBelow = footerBounds.top - hairline - groupBottom
        assertEquals("gap above the tabs $gapAbove, below $gapBelow", gapAbove, gapBelow, 1f)
    }

    // #286 TC-9b (R11)
    @Test
    fun issue286_tc9b_footerSpaceIsWhatTheTabsLeave() {
        val insets = renderEdgeToEdgeRail(footer = { space -> Box(Modifier.fillMaxWidth().height(space).testTag(FOOTER_TAG)) })

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val footerBounds = rule.onNodeWithTag(FOOTER_TAG).bounds()
        val tabs = rule.onAllNodes(TAB).fetchSemanticsNodes().map { it.boundsInRoot }
        val hairline = with(rule.density) { 0.5.dp.roundToPx().coerceAtLeast(1) }
        val usable = rail.height - insets.statusTopPx - insets.bottomInsetPx
        val tabGroupHeight = tabs.maxOf { it.bottom } - tabs.minOf { it.top }
        assertEquals("the footer's height", usable - tabGroupHeight - hairline, footerBounds.height, 1f)
        assertEquals("the first tab's top", rail.top + insets.statusTopPx, tabs.minOf { it.top }, 1f)
    }

    // #286 TC-9c (R11)
    @Test
    fun issue286_tc9c_tallFooterNeverPushesATabOff() {
        val insets = renderEdgeToEdgeRail(
            footer = { space -> Box(Modifier.fillMaxWidth().height(space + 200.dp).testTag(FOOTER_TAG)) },
        )

        val rail = rule.onNodeWithTag(RAIL_TAG).bounds()
        val tabs = rule.onAllNodes(TAB).fetchSemanticsNodes().map { it.boundsInRoot }
        tabs.forEach { tab ->
            assertTrue(
                "tab ${tab.top}..${tab.bottom} is outside the rail ${rail.top}..${rail.bottom}",
                tab.top >= rail.top - 1f && tab.bottom <= rail.bottom + 1f,
            )
        }
        assertEquals("the first tab's top", rail.top + insets.statusTopPx, tabs.minOf { it.top }, 1f)
    }

    /**
     * Lays the rail out at the start of an edge-to-edge window and returns the raw insets the rail is expected to clear.
     * [footer] is the rail's footer slot.
     */
    private fun renderEdgeToEdgeRail(footer: @Composable (Dp) -> Unit = {}): EdgeInsets {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val insets = EdgeInsets()
        rule.setContent {
            val density = LocalDensity.current
            insets.statusTopPx = WindowInsets.statusBars.getTop(density)
            insets.startInsetPx = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .getLeft(density, LayoutDirection.Ltr)
            insets.bottomInsetPx = WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(density)
            insets.railWidthPx = with(density) { 80.dp.toPx() }
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    OmnibusNavigationRail(
                        items = items(),
                        selectedIndex = 0,
                        onSelect = {},
                        modifier = Modifier.testTag(RAIL_TAG),
                        footer = footer,
                    )
                }
            }
        }
        rule.waitForIdle()
        return insets
    }

    private class EdgeInsets {
        var statusTopPx = 0
        var startInsetPx = 0
        var bottomInsetPx = 0
        var railWidthPx = 0f
    }

    private class RecordingHaptics : HapticFeedback {
        var count = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }

    private companion object {
        const val RAIL_TAG = "omnibus-navigation-rail"
        const val FOOTER_TAG = "omnibus-navigation-rail-footer"
        val TAB: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val SELECTABLE_GROUP: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
    }
}
