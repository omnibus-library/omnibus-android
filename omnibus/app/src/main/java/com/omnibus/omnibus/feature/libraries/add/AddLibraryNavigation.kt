package com.omnibus.omnibus.feature.libraries.add

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.feature.libraries.switching.SwitchingRoute

// #371
/** Opens Connect in adding mode over the stack. A second open while it is on top is ignored. */
internal fun MutableList<NavKey>.openAddLibrary() {
    if (lastOrNull() != AddLibraryConnectRoute) add(AddLibraryConnectRoute)
}

/** Opens sign-in for [address] over Connect, but only when Connect is on top. */
internal fun MutableList<NavKey>.openAddLibrarySignIn(address: String) {
    if (lastOrNull() == AddLibraryConnectRoute) add(AddLibrarySignInRoute(address))
}

/** Leaves sign-in for [route] (back to Connect), but only when it is still on top. */
internal fun MutableList<NavKey>.closeAddLibrarySignIn(route: AddLibrarySignInRoute) {
    if (lastOrNull() == route) removeAt(lastIndex)
}

/** Leaves the add flow, Connect and any sign-in above it, but only when one of them is on top. */
internal fun MutableList<NavKey>.closeAddLibrary() {
    if (lastOrNull() == AddLibraryConnectRoute || lastOrNull() is AddLibrarySignInRoute) {
        removeAll { it == AddLibraryConnectRoute || it is AddLibrarySignInRoute }
    }
}

/**
 * Sign-in saved the library [libraryId]: switch to it. Adds the switch before removing the add flow, so the stack is
 * never empty, and Stay on Sync failed returns to the shell the reader was on.
 */
internal fun MutableList<NavKey>.finishAddingLibrary(libraryId: String) {
    if (lastOrNull() !is AddLibrarySignInRoute) return
    add(SwitchingRoute(libraryId, kind = SwitchKind.Add))
    removeAll { it == AddLibraryConnectRoute || it is AddLibrarySignInRoute }
}

/**
 * Connect found [library] already saved and the reader chose to switch to it. Closes the add flow; an inactive library
 * then gets a normal switch, and the active one only closes the flow.
 */
internal fun MutableList<NavKey>.switchFromAddLibrary(library: Library) {
    if (lastOrNull() != AddLibraryConnectRoute) return
    closeAddLibrary()
    if (!library.isActive) add(SwitchingRoute(library.id))
}
