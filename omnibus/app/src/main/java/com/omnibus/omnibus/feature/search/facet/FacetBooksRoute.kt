package com.omnibus.omnibus.feature.search.facet

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.data.library.FacetKind
import kotlinx.serialization.Serializable

/** The books filed under [name] of [kind], opened from Search or a Book Details chip. */
@Serializable
data class FacetBooksRoute(val kind: FacetKind, val name: String) : NavKey
