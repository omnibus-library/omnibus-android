package com.omnibus.omnibus.feature.shell

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey

/**
 * Navigation state for the signed-in shell: one back stack per [ShellTab] and which tab is selected.
 *
 * Every tab's stack stays alive while another tab is selected, so leaving a tab and returning keeps
 * its history and, through [backStack], its saved UI state.
 *
 * @param stacks Each tab's back stack, which must begin with [ShellTab.root]. The caller supplies
 * them so they can be saveable.
 * @param selectedTab The selected tab. Written through [select], and by [back] returning to [ShellTab.Start].
 */
@Stable
class ShellNavigator(
    private val stacks: Map<ShellTab, MutableList<NavKey>>,
    private val selectedTab: MutableState<ShellTab>,
) {
    init {
        require(ShellTab.entries.all { stacks[it]?.firstOrNull() == it.root }) {
            "Every tab needs a back stack starting at its root"
        }
    }

    /** The selected tab. */
    val selected: ShellTab get() = selectedTab.value

    /**
     * Every tab's stack flattened with the selected tab's last, which is what a single `NavDisplay`
     * shows. Unselected tabs stay in the list so their entries keep their saved state.
     */
    val backStack: List<NavKey>
        get() = ShellTab.entries.filter { it != selected }.flatMap { stacks.getValue(it) } +
            stacks.getValue(selected)

    /** Selects [tab], or pops to its root if it already is selected. */
    fun select(tab: ShellTab) {
        if (tab == selected) {
            popToRoot(tab)
        } else {
            selectedTab.value = tab
        }
    }

    /** Pushes [key] onto the selected tab's stack. */
    fun navigateTo(key: NavKey) {
        stacks.getValue(selected).add(key)
    }

    /**
     * Pops the selected tab, or returns to [ShellTab.Start] from the root of any other tab.
     *
     * @return False when there was nowhere to go, so the caller should leave the shell.
     */
    fun back(): Boolean {
        val stack = stacks.getValue(selected)
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }

            selected != ShellTab.Start -> {
                selectedTab.value = ShellTab.Start
                true
            }

            else -> false
        }
    }

    private fun popToRoot(tab: ShellTab) {
        val stack = stacks.getValue(tab)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
