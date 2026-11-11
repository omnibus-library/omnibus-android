package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayer
import com.omnibus.omnibus.feature.audiobook.mini.shellHidesMiniPlayer
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.PlaceholderTitles
import com.omnibus.omnibus.feature.bookdetail.dispatch
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderScreen
import com.omnibus.omnibus.feature.bookdetail.transition.ProvideBookCoverTransition
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreen
import com.omnibus.omnibus.feature.library.ui.LibraryScreen
import com.omnibus.omnibus.feature.requests.BookRequestRoute
import com.omnibus.omnibus.feature.requests.RequestsRoute
import com.omnibus.omnibus.feature.requests.list.RequestsScreen
import com.omnibus.omnibus.feature.requests.page.BookRequestScreen
import com.omnibus.omnibus.feature.shelves.ui.ShelvesRoute
import com.omnibus.omnibus.feature.shelves.ui.ShelvesScreen
import com.omnibus.omnibus.feature.shell.LibraryRoot
import com.omnibus.omnibus.feature.shell.SearchRoot
import com.omnibus.omnibus.feature.shell.ShellNavigationLayout
import com.omnibus.omnibus.feature.shell.ShellNavigator
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.feature.shell.StatsRoot
import com.omnibus.omnibus.feature.shell.YouRoot
import com.omnibus.omnibus.feature.you.YouScreen
import com.omnibus.omnibus.ui.components.OmnibusNavigationRail
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.serialization.Serializable

@Serializable
data object SignedInShellRoute : NavKey

/**
 * The signed-in shell: a navigation stack for the selected tab. Below 600dp it is laid out under a bottom overlay of the
 * floating mini player card and the tab bar; from 600dp wide it sits beside a navigation rail instead.
 *
 * The shell doesn't inset itself for the keyboard, so the keyboard covers the tab bar rather than
 * lifting it. Screens that hold a text field handle the keyboard themselves.
 *
 * @param onOpenReader opens the reader for a book over the shell, at the given start CFI or the saved position when it
 *   is null. The Book Detail page reaches it through `BookDetailDestination.Reader`; the Continue card calls it too.
 * @param onOpenPlayer opens the player on the root stack, over the shell and its tab bar, at the given file and position
 *   (or the saved ones when they are null). Book Detail reaches it through `BookDetailDestination.Player`; the Continue
 *   card through the Library.
 */
@Composable
fun SignedInShellScreen(
    onOpenReader: (bookUuid: String, startCfi: String?) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlayer: (bookUuid: String, bookFileId: Long?, positionSeconds: Double?) -> Unit = { _, _, _ -> },
) {
    val navigator = rememberShellNavigator()
    val activity = LocalActivity.current

    SignedInShellContent(
        navigator = navigator,
        // Back from the first tab's root leaves the app, as it would without the shell.
        onBack = { if (!navigator.back()) activity?.finish() },
        onOpenReader = onOpenReader,
        onOpenPlayer = onOpenPlayer,
        modifier = modifier,
    )
}

/** Creates a [ShellNavigator] whose stacks and selection survive rotation and process death. */
@Composable
internal fun rememberShellNavigator(): ShellNavigator {
    val stacks = ShellTab.entries.associateWith { rememberNavBackStack(it.root) }
    val selectedTab = rememberSaveable { mutableStateOf(ShellTab.Start) }
    return remember(stacks) { ShellNavigator(stacks, selectedTab) }
}

@Composable
private fun SignedInShellContent(
    navigator: ShellNavigator,
    onBack: () -> Unit,
    onOpenReader: (bookUuid: String, startCfi: String?) -> Unit,
    onOpenPlayer: (bookUuid: String, bookFileId: Long?, positionSeconds: Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabBarItems = ShellTab.entries.map {
        OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon)
    }
    val uriHandler = LocalUriHandler.current
    val titles = PlaceholderTitles(
        checkIn = stringResource(R.string.book_detail_placeholder_check_in),
        settings = stringResource(R.string.book_detail_placeholder_settings),
    )
    // [owner] is the tab whose entry asked, so a link made while another tab is fading in is pushed where it was meant.
    val openDestination: (BookDetailDestination, ShellTab) -> Unit = { destination, owner ->
        destination.dispatch(
            top = navigator.top(owner),
            titles = titles,
            push = { navigator.navigateTo(it, owner) },
            openReader = onOpenReader,
            openPlayer = onOpenPlayer,
            openUri = { uriHandler.openUri(it) },
        )
    }

    val navigationLayout = rememberShellNavigationLayout()
    val selectedIndex = ShellTab.entries.indexOf(navigator.selected)
    val onSelect: (Int) -> Unit = { navigator.select(ShellTab.entries[it]) }
    // The mini player is hidden on Book Detail (P-MINI-1).
    val showsMiniPlayer = !shellHidesMiniPlayer(navigator.backStack.lastOrNull())

    // With no bar over the navigation bar the system's own scrim should stay, so only the bar layout turns it off.
    if (navigationLayout == ShellNavigationLayout.Bar) DisableNavigationBarScrim()
    ShellLayout(
        overlay = {
            ShellBottomOverlay(
                navigationLayout = navigationLayout,
                miniPlayer = { placement ->
                    if (showsMiniPlayer) MiniPlayer(onExpand = { uuid -> onOpenPlayer(uuid, null, null) }, floating = placement)
                },
                tabBar = { OmnibusTabBar(items = tabBarItems, selectedIndex = selectedIndex, onSelect = onSelect) },
            )
        },
        modifier = modifier.fillMaxSize(),
        navigationLayout = navigationLayout,
        rail = { OmnibusNavigationRail(items = tabBarItems, selectedIndex = selectedIndex, onSelect = onSelect) },
    ) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            ProvideBookCoverTransition(this) {
                NavDisplay(
                    modifier = Modifier.fillMaxSize(),
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
                        // The Library fills the window and reads the insets itself, so it takes no wrapper.
                        entry<LibraryRoot> {
                            LibraryScreen(
                                onOpenBook = { navigator.navigateTo(BookDetailRoute(it)) },
                                onAddBooks = { /* Add books sheet: later ticket */ },
                                onOpenReader = { onOpenReader(it, null) },
                                onOpenPlayer = { uuid, fileId -> onOpenPlayer(uuid, fileId, null) },
                                onOpenSeries = { _, name -> navigator.navigateTo(BookDetailPlaceholderRoute(name)) },
                                onOpenShelves = { navigator.navigateTo(ShelvesRoute) },
                            )
                        }
                        entry<BookDetailRoute> { key ->
                            val owner = remember { navigator.selected }
                            ShellUnderStatusBarEntry {
                                BookDetailScreen(
                                    uuid = key.uuid,
                                    onNavigate = { openDestination(it, owner) },
                                    onClose = { navigator.back() },
                                )
                            }
                        }
                        entry<BookDetailPlaceholderRoute> { key ->
                            ShellPaddedEntry { BookDetailPlaceholderScreen(title = key.title) }
                        }
                        entry<ShelvesRoute> {
                            ShellUnderStatusBarEntry { ShelvesScreen(onBack = { navigator.back() }) }
                        }
                        entry<BookRequestRoute> { key ->
                            val owner = remember { navigator.selected }
                            ShellUnderStatusBarEntry {
                                BookRequestScreen(
                                    uuid = key.uuid,
                                    onBack = { navigator.back() },
                                    onOpenBook = { navigator.openOrPop(BookDetailRoute(key.uuid), owner) },
                                )
                            }
                        }
                        entry<SearchRoot> { ShellPaddedEntry { UnderConstructionScreen(UnderConstructionPage.Search) } }
                        entry<StatsRoot> { ShellPaddedEntry { UnderConstructionScreen(UnderConstructionPage.Stats) } }
                        entry<YouRoot> {
                            val owner = remember { navigator.selected }
                            ShellPaddedEntry { YouScreen(onOpenRequests = { navigator.navigateTo(RequestsRoute, owner) }) }
                        }
                        entry<RequestsRoute> {
                            val owner = remember { navigator.selected }
                            ShellUnderStatusBarEntry {
                                RequestsScreen(
                                    onBack = { navigator.back() },
                                    onOpenRequest = { navigator.navigateTo(BookRequestRoute(it), owner) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
@PreviewLightDark
private fun SignedInShellScreenPreview() {
    OmnibusTheme {
        SignedInShellScreen(onOpenReader = { _, _ -> })
    }
}
