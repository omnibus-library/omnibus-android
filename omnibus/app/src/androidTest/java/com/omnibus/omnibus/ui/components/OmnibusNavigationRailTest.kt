package com.omnibus.omnibus.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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

    /**
     * Lays the rail out at the start of an edge-to-edge window and returns the raw insets the rail is expected to clear.
     */
    private fun renderEdgeToEdgeRail(): EdgeInsets {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val insets = EdgeInsets()
        rule.setContent {
            val density = LocalDensity.current
            insets.statusTopPx = WindowInsets.statusBars.getTop(density)
            insets.startInsetPx = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .getLeft(density, LayoutDirection.Ltr)
            insets.railWidthPx = with(density) { 80.dp.toPx() }
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    OmnibusNavigationRail(
                        items = items(),
                        selectedIndex = 0,
                        onSelect = {},
                        modifier = Modifier.testTag(RAIL_TAG),
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
        val TAB: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val SELECTABLE_GROUP: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
    }
}
