package com.omnibus.omnibus.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OmnibusTabBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun items() = ShellTab.entries.map {
        OmnibusTabBarItem(context.getString(it.label), it.icon, it.selectedIcon)
    }

    // TC-12
    @Test
    fun tc12_exposesTabsInAGroup() {
        val labels = ShellTab.entries.map { context.getString(it.label) }
        val searchIndex = ShellTab.entries.indexOf(ShellTab.Search)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                OmnibusTabBar(items = items(), selectedIndex = searchIndex, onSelect = {})
            }
        }

        rule.onAllNodes(TAB).assertCountEquals(ShellTab.entries.size)
        rule.onAllNodes(TAB and hasParent(SELECTABLE_GROUP)).assertCountEquals(ShellTab.entries.size)
        rule.onAllNodes(TAB and isSelected()).assertCountEquals(1)
        rule.onNode(TAB and isSelected()).assertTextEquals(labels[searchIndex])
        val shownLabels = rule.onAllNodes(TAB).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.left }
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
                OmnibusTabBar(items = items(), selectedIndex = 0, onSelect = {})
            }
        }

        rule.onAllNodes(TAB).assertCountEquals(ShellTab.entries.size)
        for (index in ShellTab.entries.indices) {
            rule.onAllNodes(TAB)[index]
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    private companion object {
        val TAB: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val SELECTABLE_GROUP: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
    }
}
