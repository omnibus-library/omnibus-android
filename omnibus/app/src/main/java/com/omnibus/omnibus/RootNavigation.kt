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
import com.omnibus.omnibus.feature.audiobook.player.PlayerRoute
import com.omnibus.omnibus.feature.auth.ui.LoginScreenRoute
import com.omnibus.omnibus.feature.libraries.switching.SwitchingRoute
import com.omnibus.omnibus.feature.reader.ui.ReaderRoute
import com.omnibus.omnibus.feature.scan.ScanRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute

/**
 * Whether the user should be sent to sign-in: the session is known to be gone (an explicit false,
 * not the not-yet-read null) and the shell is still on the stack, wherever it sits under the top.
 * A sign-out that arrives while a switch is on top waits until the reader stays (the switch's sync can end with a 401).
 */
internal fun shouldLeaveForLogin(backstack: List<NavKey>, isSignedIn: Boolean?): Boolean =
    isSignedIn == false && SignedInShellRoute in backstack && backstack.lastOrNull() !is SwitchingRoute

/** Replaces the whole stack with the sign-in screen. Adds before removing, so the stack is never empty. */
internal fun MutableList<NavKey>.leaveForLogin() {
    add(LoginScreenRoute)
    removeAll { it != LoginScreenRoute }
}

/**
 * Whether the route on top draws its own insets. The root Scaffold would otherwise pad it, which
 * would keep the shell's tab bar above the keyboard and stop the reader and the player going edge to edge.
 */
internal fun drawsOwnInsets(top: NavKey?): Boolean =
    top == SignedInShellRoute || top is ReaderRoute || top is PlayerRoute || top == ScanRoute || top is SwitchingRoute

/** Opens the scanner over the shell. A second open while it is on top is ignored. */
internal fun MutableList<NavKey>.openScanner() {
    if (lastOrNull() != ScanRoute) add(ScanRoute)
}

/** Leaves the scanner, but only when it is still on top. */
internal fun MutableList<NavKey>.closeScanner() {
    if (lastOrNull() == ScanRoute) removeAt(lastIndex)
}

// #369
/** Opens the switching screen for [libraryId] over the stack. A second open while it is on top is ignored. */
internal fun MutableList<NavKey>.openSwitching(libraryId: String) {
    if (lastOrNull() !is SwitchingRoute) add(SwitchingRoute(libraryId))
}

/** Leaves the switching screen for [route], but only when it is still on top. */
internal fun MutableList<NavKey>.closeSwitching(route: SwitchingRoute) {
    if (lastOrNull() == route) removeAt(lastIndex)
}

/** Ends an Opening stage: the shell replaces the whole stack, so the reader lands on the Library tab with no history. */
internal fun MutableList<NavKey>.finishOpening() {
    // Adds the shell before removing anything, so the stack is never empty. A shell already in the stack is kept once.
    if (SignedInShellRoute !in this) add(SignedInShellRoute)
    removeAll { it != SignedInShellRoute }
}

/**
 * Opens the reader for [bookUuid] over the stack, at [startCfi] when given. A second open of the same route on top is
 * ignored; a different [startCfi] is pushed.
 */
internal fun MutableList<NavKey>.openReader(bookUuid: String, startCfi: String? = null) {
    val route = ReaderRoute(bookUuid, startCfi)
    if (lastOrNull() != route) add(route)
}

/** Leaves the reader for [route], but only when it is still on top, so a stale back can't pop anything else. */
internal fun MutableList<NavKey>.closeReader(route: ReaderRoute) {
    if (lastOrNull() == route) removeAt(lastIndex)
}

/**
 * Opens the player for [bookUuid] over the stack. A second open of the same route on top is ignored; a different file
 * or position is pushed.
 */
internal fun MutableList<NavKey>.openPlayer(bookUuid: String, bookFileId: Long?, positionSeconds: Double? = null) {
    val route = PlayerRoute(bookUuid, bookFileId, positionSeconds)
    if (lastOrNull() != route) add(route)
}

/** Collapses the player for [route], but only when it is still on top. Playback keeps going. */
internal fun MutableList<NavKey>.closePlayer(route: PlayerRoute) {
    if (lastOrNull() == route) removeAt(lastIndex)
}

/**
 * The root navigation display. It hosts the shell, the reader, the player and the sign-in screens on one stack,
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
    // Null means the session hasn't been read yet, so only an explicit false leaves the shell. Keyed on the top too,
    // so a sign-out held back by a switch is acted on once the switching screen is gone.
    val top = backstack.lastOrNull()
    LaunchedEffect(isSignedIn, top) {
        if (shouldLeaveForLogin(backstack, isSignedIn)) backstack.leaveForLogin()
    }

    // The shell, the reader and the player inset themselves, so the Scaffold must not pad them.
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
                // The switching screen handles Back itself, so Back never leaves it from here.
                if (backstack.lastOrNull() !is SwitchingRoute) backstack.removeLastOrNull()
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider,
        )
    }
}
