package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayer
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.PlaceholderTitles
import com.omnibus.omnibus.feature.bookdetail.dispatch
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderScreen
import com.omnibus.omnibus.feature.bookdetail.transition.ProvideBookCoverTransition
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreen
import com.omnibus.omnibus.feature.library.ui.LibraryScreen
import com.omnibus.omnibus.feature.shell.LibraryRoot
import com.omnibus.omnibus.feature.shell.ShellNavigator
import com.omnibus.omnibus.feature.shell.SearchRoot
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.feature.shell.chrome.FabSpec
import com.omnibus.omnibus.feature.shell.StatsRoot
import com.omnibus.omnibus.feature.shell.YouRoot
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.serialization.Serializable

@Serializable
data object SignedInShellRoute : NavKey

/**
 * The signed-in shell: a navigation stack for the selected tab, drawn full height behind a floating tab bar, mini
 * player and FAB (I-ARCH-1).
 *
 * The shell doesn't inset itself for the keyboard, so the keyboard covers the chrome rather than lifting it (P-VIS-5).
 * Screens that hold a text field handle the keyboard themselves.
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
private fun rememberShellNavigator(): ShellNavigator {
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
    val palette = OmnibusTheme.palette
    val tabBarItems = ShellTab.entries.map {
        OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon)
    }
    val uriHandler = LocalUriHandler.current
    val titles = PlaceholderTitles(
        checkIn = stringResource(R.string.book_detail_placeholder_check_in),
        settings = stringResource(R.string.book_detail_placeholder_settings),
    )
    val openDestination: (BookDetailDestination) -> Unit = { destination ->
        destination.dispatch(
            top = navigator.backStack.lastOrNull(),
            titles = titles,
            push = { navigator.navigateTo(it) },
            openReader = onOpenReader,
            openPlayer = onOpenPlayer,
            openUri = { uriHandler.openUri(it) },
        )
    }

    val chrome = navigator.chromeState
    val reducedMotion = rememberReducedMotion()
    // The Add books sheet is a later ticket (#16, H00-107). Until then the FAB calls this stub.
    val onAddBooks: () -> Unit = { }
    val libraryFab = remember { FabSpec(R.drawable.ic_add_book, R.string.masthead_add_books, onAddBooks) }
    // The selected root's FAB (I-STATE-2). Only Library has one.
    val fabSpec = if (navigator.selected == ShellTab.Library) libraryFab else null
    val fabShown = chrome.fabVisible && fabSpec != null
    // Keeps the last FAB so its exit animation still draws it after the selected root stops supplying one.
    val lastFab = remember { LastFab() }
    if (fabSpec != null) lastFab.spec = fabSpec

    ChromeScaffold(
        chromeVisible = chrome.visible,
        fabVisible = fabShown,
        reducedMotion = reducedMotion,
        modifier = modifier.background(palette.bg0),
        bar = {
            OmnibusTabBar(
                items = tabBarItems,
                selectedIndex = ShellTab.entries.indexOf(navigator.selected),
                onSelect = { navigator.select(ShellTab.entries[it]) },
                enabled = chrome.visible,
                reducedMotion = reducedMotion,
            )
        },
        fab = { lastFab.spec?.let { ShellFab(it, enabled = fabShown) } },
        // Unchanged inside; placed in the I-MINI-1 slot (I-MINI-3). It hides with the chrome (I-MINI-2).
        miniPlayer = { MiniPlayer(onExpand = { uuid -> onOpenPlayer(uuid, null, null) }) },
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
                        entry<LibraryRoot> {
                            StatusBarPadded {
                                LibraryScreen(
                                    onOpenBook = { navigator.navigateTo(BookDetailRoute(it)) },
                                    onOpenReader = { onOpenReader(it, null) },
                                    onOpenPlayer = { uuid, fileId -> onOpenPlayer(uuid, fileId, null) },
                                )
                            }
                        }
                        entry<BookDetailRoute> { key ->
                            BookDetailScreen(
                                uuid = key.uuid,
                                onNavigate = openDestination,
                                onClose = { navigator.back() },
                            )
                        }
                        entry<BookDetailPlaceholderRoute> { key ->
                            StatusBarPadded { BookDetailPlaceholderScreen(title = key.title) }
                        }
                        entry<SearchRoot> { StatusBarPadded { EmptyTabRoot(ShellTab.Search) } }
                        entry<StatsRoot> { StatusBarPadded { EmptyTabRoot(ShellTab.Stats) } }
                        entry<YouRoot> { StatusBarPadded { EmptyTabRoot(ShellTab.You) } }
                    },
                )
            }
        }
    }
}

/** Keeps the last FAB so its exit still draws it. A plain class, so writing it during composition is safe. */
private class LastFab(var spec: FabSpec? = null)

/** Pads the status bar for an entry. Book Detail draws under it, so the shell can't pad the whole NavDisplay. */
@Composable
private fun StatusBarPadded(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) { content() }
}

/** Placeholder root until the tab's own ticket supplies its content. It clears the floating chrome (I-ARCH-2). */
@Composable
private fun EmptyTabRoot(tab: ShellTab, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(LocalShellContentPadding.current),
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
        SignedInShellScreen(onOpenReader = { _, _ -> })
    }
}
