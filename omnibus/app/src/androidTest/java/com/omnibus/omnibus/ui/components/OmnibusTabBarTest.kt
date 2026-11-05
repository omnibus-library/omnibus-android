package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OmnibusTabBarTest {

    @get:Rule
    val rule = createComposeRule()

    /** The tab labels in bar order (I-NAV-1). */
    private val labels = listOf("Library", "Search", "Stats", "You")

    @Test
    fun tabsAreInOrder() {
        // TC-32: labels in order, with strictly increasing left edges (LTR)
        setBar()

        val lefts = labels.indices.map { rule.onNodeWithTag(OmnibusTabBarTestTags.tab(it)).getBoundsInRoot().left }
        assertTrue("tabs should run left to right, got $lefts", lefts.zipWithNext().all { (a, b) -> a < b })
        labels.forEachIndexed { index, label ->
            rule.onNodeWithTag(OmnibusTabBarTestTags.tab(index)).assertTextEquals(label)
        }
    }

    @Test
    fun tabsAreSelectableTabs() {
        // TC-33: every tab has the Tab role, and only the selected one is selected
        setBar(selectedIndex = 2)

        labels.indices.forEach { index ->
            val tab = rule.onNodeWithTag(OmnibusTabBarTestTags.tab(index))
            tab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            if (index == 2) tab.assertIsSelected() else tab.assertIsNotSelected()
        }
    }

    @Test
    fun tapSelectsWithOneTickAndReselectIsSilent() {
        // TC-34: a tap on another tab selects it with one SegmentTick; a tap on the selected tab selects it silently
        val haptics = RecordingHaptics()
        val selections = mutableListOf<Int>()
        setBar(selectedIndex = 0, haptics = haptics, onSelect = { selections += it })

        rule.onNodeWithTag(OmnibusTabBarTestTags.tab(2)).performClick()
        assertEquals(listOf(2), selections)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics.performed)

        rule.onNodeWithTag(OmnibusTabBarTestTags.tab(0)).performClick()
        assertEquals(listOf(2, 0), selections)
        assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics.performed)
    }

    @Test
    fun disabledBarIgnoresTaps() {
        // TC-35: a disabled bar takes no taps
        val selections = mutableListOf<Int>()
        setBar(enabled = false, onSelect = { selections += it })

        rule.onNodeWithTag(OmnibusTabBarTestTags.tab(1)).performClick()

        assertTrue("a disabled bar should ignore taps, got $selections", selections.isEmpty())
    }

    @Test
    fun largeFontGrowsTheBarAndKeepsLabelsOnOneLine() {
        // TC-36: at 2x font scale the bar grows, and every label stays on one line (P-A11Y-3)
        val fontScale = mutableFloatStateOf(1f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale.floatValue)) {
                OmnibusTheme(darkTheme = true) {
                    TestBar(selectedIndex = 0, enabled = true, onSelect = {})
                }
            }
        }
        val normalHeight = rule.onNodeWithTag(OmnibusTabBarTestTags.BAR).getBoundsInRoot().heightDp

        fontScale.floatValue = 2f
        rule.waitForIdle()
        val largeHeight = rule.onNodeWithTag(OmnibusTabBarTestTags.BAR).getBoundsInRoot().heightDp
        assertTrue("the bar should grow at 2x font scale, was $normalHeight then $largeHeight", largeHeight > normalHeight)

        labels.forEach { label ->
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(label, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action
                ?.invoke(results)
            assertEquals("$label should stay on one line", 1, results.single().lineCount)
        }
    }

    @Test
    fun tabsAreAtLeast48dp() {
        // AND-A11Y-1: every target is at least 48 dp wide and tall
        setBar()

        labels.indices.forEach { index ->
            val bounds = rule.onNodeWithTag(OmnibusTabBarTestTags.tab(index)).getBoundsInRoot()
            assertTrue("tab $index is ${bounds.widthDp} wide", bounds.widthDp >= 48.dp)
            assertTrue("tab $index is ${bounds.heightDp} tall", bounds.heightDp >= 48.dp)
        }
    }

    private fun setBar(
        selectedIndex: Int = 0,
        enabled: Boolean = true,
        haptics: HapticFeedback = RecordingHaptics(),
        onSelect: (Int) -> Unit = {},
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OmnibusTheme(darkTheme = true) {
                    TestBar(selectedIndex = selectedIndex, enabled = enabled, onSelect = onSelect)
                }
            }
        }
    }

    @Composable
    private fun TestBar(selectedIndex: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
        val items = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) }
        OmnibusTabBar(
            items = items,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }

    private val DpRect.widthDp get() = right - left

    private val DpRect.heightDp get() = bottom - top

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }
}
