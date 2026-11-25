package com.omnibus.omnibus.data.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A taxonomy a books page can be narrowed to. [apiField] is the server's filter field. */
@Serializable
enum class FacetKind(val apiField: String) { Tag("tag") }

/** Only the books filed under exactly [name] of [kind] (the server ignores case). */
data class BooksFacet(val kind: FacetKind, val name: String)

/** The `filter` value for this facet: one include clause, as JSON, so any character in [BooksFacet.name] is escaped. */
internal fun BooksFacet.toFilterJson(): String = buildJsonArray {
    addJsonObject {
        put("field", kind.apiField)
        put("mode", "include")
        putJsonArray("values") { add(name) }
    }
}.toString()
