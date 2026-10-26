package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.omnibus.omnibus.feature.shell.AuthorsRoot
import com.omnibus.omnibus.feature.shell.LibraryRoot
import com.omnibus.omnibus.feature.shell.SeriesRoot
import com.omnibus.omnibus.feature.shell.ShellNavigator
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.feature.shell.StatsRoot
import com.omnibus.omnibus.feature.shell.YouRoot
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.serialization.Serializable

@Serializable
data object SignedInShellRoute : NavKey

/**
 * The signed-in shell: a tab bar over a navigation stack for the selected tab.
 *
 * The shell doesn't inset itself for the keyboard, so the keyboard covers the tab bar rather than
 * lifting it. Screens that hold a text field handle the keyboard themselves.
 */
@Composable
fun SignedInShellScreen(modifier: Modifier = Modifier) {
    val navigator = rememberShellNavigator()
    val activity = LocalActivity.current

    SignedInShellContent(
        navigator = navigator,
        // Back from the first tab's root leaves the app, as it would without the shell.
        onBack = { if (!navigator.back()) activity?.finish() },
        modifier = modifier,
    )
}

/** Creates a [ShellNavigator] whose stacks and selection survive rotation and process death. */
@Composable
private fun rememberShellNavigator(): ShellNavigator {
    val stacks = ShellTab.entries.associateWith { rememberNavBackStack(it.root) }
    val selectedTab = rememberSaveable { mutableStateOf(ShellTab.Start) }
    return remember(stacks) { ShellNavigator(stacks, selectedTab) }
}

@Composable
private fun SignedInShellContent(
    navigator: ShellNavigator,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val tabBarItems = ShellTab.entries.map {
        OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg0),
    ) {
        NavDisplay(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.statusBars),
            backStack = navigator.backStack,
            onBack = onBack,
            // Switching tabs moves between stacks, which a slide would read as a push or pop.
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            popTransitionSpec = { fadeIn() togetherWith fadeOut() },
            predictivePopTransitionSpec = { fadeIn() togetherWith fadeOut() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<LibraryRoot> { EmptyTabRoot(ShellTab.Library) }
                entry<AuthorsRoot> { EmptyTabRoot(ShellTab.Authors) }
                entry<SeriesRoot> { EmptyTabRoot(ShellTab.Series) }
                entry<StatsRoot> { EmptyTabRoot(ShellTab.Stats) }
                entry<YouRoot> { EmptyTabRoot(ShellTab.You) }
            },
        )
        OmnibusTabBar(
            items = tabBarItems,
            selectedIndex = ShellTab.entries.indexOf(navigator.selected),
            onSelect = { navigator.select(ShellTab.entries[it]) },
        )
    }
}

/** Placeholder root until the tab's own ticket supplies its content. */
@Composable
private fun EmptyTabRoot(tab: ShellTab, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(tab.label),
            style = uiTextStyle(16, FontWeight.Normal, OmnibusTheme.palette.ink0),
        )
    }
}

@Composable
@PreviewLightDark
private fun SignedInShellScreenPreview() {
    OmnibusTheme {
        SignedInShellScreen()
    }
}
