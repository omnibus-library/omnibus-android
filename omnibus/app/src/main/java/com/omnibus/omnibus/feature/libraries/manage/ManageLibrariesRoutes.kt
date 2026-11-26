package com.omnibus.omnibus.feature.libraries.manage

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Manage libraries, pushed on the You tab's stack. */
@Serializable
data object ManageLibrariesRoute : NavKey

/**
 * One library's detail screen, pushed on the You tab's stack.
 *
 * @property libraryId The library's [com.omnibus.omnibus.data.libraries.Library.id].
 */
@Serializable
data class LibraryDetailRoute(val libraryId: String) : NavKey
