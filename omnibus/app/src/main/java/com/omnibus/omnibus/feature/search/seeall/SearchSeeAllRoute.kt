package com.omnibus.omnibus.feature.search.seeall

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.search.SearchSectionKind
import kotlinx.serialization.Serializable

/** Every match of one section for [query], pushed on the Search stack. */
@Serializable
data class SearchSeeAllRoute(val section: SearchSectionKind, val query: String) : NavKey
