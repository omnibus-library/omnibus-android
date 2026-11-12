package com.omnibus.omnibus.feature.shelves.detail.edit

import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp

/** The read statuses a status condition can test, as the server's wire tokens. */
private val STATUS_VALUES = listOf("unread", "reading", "finished")

/** The star ratings a rating condition can test, 0.5 to 5 in half steps, in the form the editor writes them. */
private val RATING_VALUES = (1..10).map { half -> if (half % 2 == 0) (half / 2).toString() else "${half / 2}.5" }

/** The formats a format condition can name. */
private val FORMAT_VALUES = (LibraryCategory.Ebooks.formats + LibraryCategory.Audiobooks.formats).toList()

/**
 * The values a condition on [field] with [op] can take when they are a small fixed set, so the editor offers them as a
 * menu: a status, a star rating, or a format with "has a file in". Null when the value is free text, such as a name, a
 * year, a date or a partial format.
 */
fun fixedValuesOf(field: ShelfRuleField, op: ShelfRuleOp): List<String>? = when {
    field == ShelfRuleField.Status -> STATUS_VALUES
    field == ShelfRuleField.Rating -> RATING_VALUES
    field == ShelfRuleField.Format && op == ShelfRuleOp.Includes -> FORMAT_VALUES
    else -> null
}

/**
 * The entry of [options] that [value] names, ignoring case and, for a rating, how the number is written ("4.0" is
 * "4"), or null when none does.
 */
fun canonicalValue(options: List<String>, value: String): String? {
    val wanted = value.trim()
    options.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
    val number = wanted.toDoubleOrNull() ?: return null
    return options.firstOrNull { it.toDoubleOrNull() == number }
}

/**
 * This rule's value made to fit its field and op: a fixed set keeps the value it names and drops any other, and a
 * move from a fixed set to free text drops the value, which would mean nothing there. A rule moving between free-text
 * conditions keeps its value.
 */
internal fun EditableRule.fittingValueFrom(previous: EditableRule): EditableRule {
    val options = fixedValuesOf(field, op)
    return when {
        options != null -> copy(value = canonicalValue(options, value) ?: "")
        fixedValuesOf(previous.field, previous.op) != null -> copy(value = "")
        else -> this
    }
}
