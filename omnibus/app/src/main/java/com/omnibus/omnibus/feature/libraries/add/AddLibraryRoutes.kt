package com.omnibus.omnibus.feature.libraries.add

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Connect in adding mode (#371): the address screen, over the shell, with a close button. */
@Serializable
data object AddLibraryConnectRoute : NavKey

/** Sign-in in adding mode (#371) for [address], a library that isn't saved yet. Connect stays under it. */
@Serializable
data class AddLibrarySignInRoute(val address: String) : NavKey
