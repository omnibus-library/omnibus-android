package com.omnibus.omnibus

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.omnibus.omnibus.feature.auth.ui.LoginScreenRoute
import com.omnibus.omnibus.feature.reader.ui.ReaderRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute

/**
 * Whether the user should be sent to sign-in: the session is known to be gone (an explicit false,
 * not the not-yet-read null) and the shell is still on the stack, wherever it sits under the top.
 */
internal fun shouldLeaveForLogin(backstack: List<NavKey>, isSignedIn: Boolean?): Boolean =
    isSignedIn == false && SignedInShellRoute in backstack

/** Replaces the whole stack with the sign-in screen. Adds before removing, so the stack is never empty. */
internal fun MutableList<NavKey>.leaveForLogin() {
    add(LoginScreenRoute)
    removeAll { it != LoginScreenRoute }
}

/**
 * Whether the route on top draws its own insets. The root Scaffold would otherwise pad it, which
 * would keep the shell's tab bar above the keyboard and stop the reader going edge to edge.
 */
internal fun drawsOwnInsets(top: NavKey?): Boolean = top == SignedInShellRoute || top is ReaderRoute

/** Opens the reader for [bookUuid] over the stack. A second open of the same book on top is ignored. */
internal fun MutableList<NavKey>.openReader(bookUuid: String) {
    val route = ReaderRoute(bookUuid)
    if (lastOrNull() != route) add(route)
}

/** Leaves the reader for [route], but only when it is still on top, so a stale back can't pop anything else. */
internal fun MutableList<NavKey>.closeReader(route: ReaderRoute) {
    if (lastOrNull() == route) removeAt(lastIndex)
}

/**
 * The root navigation display. It hosts the shell, the reader and the sign-in screens on one stack,
 * so the reader covers the shell and its tab bar, and leaving the reader restores the shell.
 *
 * @param backstack the root stack, which owns the screens' order.
 * @param isSignedIn null until the session is read; an explicit false sends the user to sign-in.
 * @param entryProvider builds each destination's entry. Routes are declared in [MainActivity].
 */
@Composable
internal fun RootNavDisplay(
    backstack: MutableList<NavKey>,
    isSignedIn: Boolean?,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    // Null means the session hasn't been read yet, so only an explicit false leaves the shell.
    LaunchedEffect(isSignedIn) {
        if (shouldLeaveForLogin(backstack, isSignedIn)) backstack.leaveForLogin()
    }

    // The shell and the reader inset themselves, so the Scaffold must not pad them.
    val insets = if (drawsOwnInsets(backstack.lastOrNull())) {
        WindowInsets(0)
    } else {
        ScaffoldDefaults.contentWindowInsets
    }

    Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = insets) { innerPadding ->
        NavDisplay(
            modifier = Modifier.padding(innerPadding),
            backStack = backstack,
            onBack = {
                backstack.removeLastOrNull()
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider,
        )
    }
}
