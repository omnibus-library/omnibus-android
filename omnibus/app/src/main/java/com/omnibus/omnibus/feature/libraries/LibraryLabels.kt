package com.omnibus.omnibus.feature.libraries

import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibraryHost

/** The name shown for a library: its name, or its host when the name is blank (C5). */
fun Library.displayName(): String = name.ifBlank { LibraryHost.of(address) }

/**
 * The account line under a library's name, "‹username› · ‹host›" (C5). While the username is unknown it is the
 * host alone, so the separator never shows without a username.
 */
fun Library.accountLine(): String {
    val host = LibraryHost.of(address)
    return if (username == null) host else "$username · $host"
}
