package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.data.shelves.remote.ShelvesJson
import kotlinx.serialization.Serializable

/** The most values one clause may carry, as the server's `view_filters` limit. */
const val MAX_FILTER_VALUES = 64

/** The book fields a shelf view can be filtered by. [wire] is the token the server uses; declaration order is the facet order. */
enum class FilterField(val wire: String) {
    Shelf("shelf"),
    Tag("tag"),
    Genre("genre"),
    Author("author"),
    Series("series"),
    Format("format"),
}

/** Whether a clause keeps books with one of its values or drops them. [wire] is the token the server uses. */
enum class FilterMode(val wire: String) {
    Include("include"),
    Exclude("exclude"),
}

/**
 * One facet of a shelf view. Values are ORed within the clause, and clauses are ANDed.
 *
 * @property values The picked values: tag or genre names, author or series names, shelf ids as decimal strings,
 * or format tokens.
 */
data class FilterClause(
    val field: FilterField,
    val mode: FilterMode,
    val values: List<String>,
)

/**
 * The `filter` query value for [clauses]: a compact JSON array such as
 * `[{"field":"genre","mode":"include","values":["Fantasy"]}]`. Values are trimmed, blank values are dropped,
 * format values are lower-cased, and a clause left with no values is omitted.
 *
 * @return The encoded array, or null when no clause is left to send.
 */
fun filterQueryValue(clauses: List<FilterClause>): String? {
    val dtos = clauses.mapNotNull { clause ->
        val values = clause.values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (clause.field == FilterField.Format) it.lowercase() else it }
        if (values.isEmpty()) {
            null
        } else {
            FilterClauseDto(field = clause.field.wire, mode = clause.mode.wire, values = values)
        }
    }
    if (dtos.isEmpty()) return null
    return ShelvesJson.encodeToString(dtos)
}

/** One clause as the server reads it. Key order is the order the server documents. */
@Serializable
private data class FilterClauseDto(
    val field: String,
    val mode: String,
    val values: List<String>,
)
