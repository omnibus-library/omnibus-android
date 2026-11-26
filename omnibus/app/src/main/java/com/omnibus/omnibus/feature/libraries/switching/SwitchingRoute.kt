package com.omnibus.omnibus.feature.libraries.switching

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.data.libraries.SwitchKind
import kotlinx.serialization.Serializable

/**
 * The switching screen for a switch to [toLibraryId]. A root route above the shell. After the restart it is the only
 * route, restored with [atOpening] so it shows the Opening stage until the shell takes over.
 */
@Serializable
data class SwitchingRoute(
    val toLibraryId: String,
    val kind: SwitchKind = SwitchKind.Switch,
    val atOpening: Boolean = false,
) : NavKey
