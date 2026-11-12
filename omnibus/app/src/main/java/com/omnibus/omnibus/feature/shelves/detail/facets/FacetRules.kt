package com.omnibus.omnibus.feature.shelves.detail.facets

import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.FilterMode
import com.omnibus.omnibus.data.shelves.MAX_FILTER_VALUES

/** One facet of the filter row: its field, and the clause set on it, if any. */
data class FacetChip(val field: FilterField, val clause: FilterClause?)

/** The label of a set facet: its first value, how many more follow it, and whether the clause excludes. */
data class FacetLabel(val field: FilterField, val excluded: Boolean, val first: String?, val more: Int)

/**
 * The facet row's chips: the set facets first, in field order, then the unset ones, also in field order. Every field
 * has a chip.
 */
fun facetChips(clauses: List<FilterClause>): List<FacetChip> {
    val active = FilterField.entries.mapNotNull { field ->
        clauses.firstOrNull { it.field == field }?.let { FacetChip(field, it) }
    }
    val inactive = FilterField.entries
        .filter { field -> clauses.none { it.field == field } }
        .map { FacetChip(it, null) }
    return active + inactive
}

/** Whether the row offers Clear: only while some facet is set. */
fun showsClear(clauses: List<FilterClause>): Boolean = clauses.isNotEmpty()

/**
 * The label of [chip]. A format value is upper-cased. A shelf value is the shelf's name, or null when [shelfName]
 * doesn't know it, so the chip can say the shelf is unavailable.
 */
fun facetLabelOf(chip: FacetChip, shelfName: (Long) -> String?): FacetLabel {
    val clause = chip.clause ?: return FacetLabel(chip.field, excluded = false, first = null, more = 0)
    val raw = clause.values.firstOrNull() ?: return FacetLabel(chip.field, excluded = false, first = null, more = 0)
    val first = when (clause.field) {
        FilterField.Format -> raw.uppercase()
        FilterField.Shelf -> raw.toLongOrNull()?.let(shelfName)
        else -> raw
    }
    return FacetLabel(
        field = chip.field,
        excluded = clause.mode == FilterMode.Exclude,
        first = first,
        more = clause.values.size - 1,
    )
}

/**
 * These clauses with [clause] applied: it replaces the clause on the same field, in its place, or is added at the end.
 * A clause with no values removes its field's clause instead. Other fields keep their order.
 */
fun List<FilterClause>.withClause(clause: FilterClause): List<FilterClause> {
    if (clause.values.isEmpty()) return filterNot { it.field == clause.field }
    val index = indexOfFirst { it.field == clause.field }
    return if (index >= 0) toMutableList().also { it[index] = clause } else this + clause
}

/**
 * [picked] with [value] toggled: removed when already there (ignoring case), else added. A value past
 * [MAX_FILTER_VALUES] is refused.
 */
fun toggleValue(picked: List<String>, value: String): List<String> {
    val existing = picked.indexOfFirst { it.equals(value, ignoreCase = true) }
    return when {
        existing >= 0 -> picked.filterIndexed { index, _ -> index != existing }
        picked.size < MAX_FILTER_VALUES -> picked + value
        else -> picked
    }
}
