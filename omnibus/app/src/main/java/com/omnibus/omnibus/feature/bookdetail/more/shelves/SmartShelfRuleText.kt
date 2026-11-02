package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules

/**
 * The localized words a smart shelf's rule text is built from. [rememberRuleWords] loads them from
 * resources; tests pass English lambdas.
 *
 * @property op Turns an op and its value into a phrase such as "is 1901".
 * @property inLast Turns a count and a unit letter (d, w, m or y) into "2 weeks".
 * @property status The word for a status token (unread, reading or finished), or null for any other.
 */
internal data class RuleWords(
    val field: (ShelfRuleField) -> String,
    val op: (ShelfRuleOp, String) -> String,
    val between: (String, String) -> String,
    val inLast: (Int, Char) -> String,
    val status: (String) -> String?,
    val clause: (String, String) -> String,
    val and: (String, String) -> String,
    val or: (String, String) -> String,
    val more: (String, Int) -> String,
)

/** An `in_last` window: a count and a unit letter, such as `2w`. */
private val IN_LAST = Regex("""(\d+)([dwmy])""")

/**
 * The rule text for [rules], such as "year is at least 1901", or null when any rule can't be
 * summarised, so a half sentence never shows. Rules are combined by the match mode: two read
 * "A and B" or "A or B", three or more read "A + N more".
 */
internal fun smartShelfRuleText(rules: ShelfRules, words: RuleWords): String? {
    if (rules.rules.isEmpty()) return null
    val clauses = rules.rules.map { clauseOf(it, words) ?: return null }
    return when (clauses.size) {
        1 -> clauses.single()
        2 -> if (rules.matchMode == ShelfMatchMode.Any) {
            words.or(clauses[0], clauses[1])
        } else {
            words.and(clauses[0], clauses[1])
        }
        else -> words.more(clauses.first(), clauses.size - 1)
    }
}

/** One rule as a clause, or null when the field, op or value isn't one the server accepts. */
private fun clauseOf(rule: ShelfRule, words: RuleWords): String? {
    val field = rule.field ?: return null
    val op = rule.op ?: return null
    if (op !in validOps(field)) return null
    val value = rule.value.trim()
    if (value.isBlank()) return null

    val opPhrase = when {
        field == ShelfRuleField.Status -> words.status(value.lowercase())?.let { words.op(op, it) } ?: return null
        op == ShelfRuleOp.InLast -> {
            val window = IN_LAST.matchEntire(value) ?: return null
            val count = window.groupValues[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
            words.op(op, words.inLast(count, window.groupValues[2].single()))
        }
        op == ShelfRuleOp.Between -> {
            val parts = value.split("..")
            if (parts.size != 2) return null
            val from = parts[0].trim()
            val to = parts[1].trim()
            if (from.isBlank() || to.isBlank()) return null
            // The between phrase already carries its op word.
            words.between(from, to)
        }
        else -> words.op(op, value)
    }
    return words.clause(words.field(field), opPhrase)
}

/** The ops the server accepts for [field], as its `RuleField::accepts` table lists them. */
private fun validOps(field: ShelfRuleField): Set<ShelfRuleOp> = when (field) {
    ShelfRuleField.Tag, ShelfRuleField.Genre, ShelfRuleField.Author, ShelfRuleField.Series ->
        setOf(ShelfRuleOp.Is, ShelfRuleOp.IsNot, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)
    ShelfRuleField.Rating, ShelfRuleField.Year -> setOf(ShelfRuleOp.Is, ShelfRuleOp.Gte)
    ShelfRuleField.Status -> setOf(ShelfRuleOp.Is)
    ShelfRuleField.Format -> setOf(ShelfRuleOp.Includes, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)
    ShelfRuleField.DateAdded, ShelfRuleField.DateUpdated ->
        setOf(ShelfRuleOp.InLast, ShelfRuleOp.Between, ShelfRuleOp.Before, ShelfRuleOp.After)
}

/** The [RuleWords] for the current locale, remembered for the resources they come from. */
@Composable
internal fun rememberRuleWords(): RuleWords {
    val resources = LocalContext.current.resources
    return remember(resources) {
        RuleWords(
            field = { field -> resources.getString(fieldName(field)) },
            op = { op, value -> resources.getString(opName(op), value) },
            between = { from, to -> resources.getString(R.string.book_detail_more_rule_op_between, from, to) },
            inLast = { count, unit -> resources.getQuantityString(unitName(unit), count, count) },
            status = { value -> statusName(value)?.let { resources.getString(it) } },
            clause = { field, op -> resources.getString(R.string.book_detail_more_rule_clause, field, op) },
            and = { first, second -> resources.getString(R.string.book_detail_more_rule_and, first, second) },
            or = { first, second -> resources.getString(R.string.book_detail_more_rule_or, first, second) },
            more = { first, count -> resources.getString(R.string.book_detail_more_rule_more, first, count) },
        )
    }
}

private fun fieldName(field: ShelfRuleField): Int = when (field) {
    ShelfRuleField.Tag -> R.string.book_detail_more_rule_field_tag
    ShelfRuleField.Genre -> R.string.book_detail_more_rule_field_genre
    ShelfRuleField.Author -> R.string.book_detail_more_rule_field_author
    ShelfRuleField.Series -> R.string.book_detail_more_rule_field_series
    ShelfRuleField.Rating -> R.string.book_detail_more_rule_field_rating
    ShelfRuleField.Status -> R.string.book_detail_more_rule_field_status
    ShelfRuleField.Format -> R.string.book_detail_more_rule_field_format
    ShelfRuleField.Year -> R.string.book_detail_more_rule_field_year
    ShelfRuleField.DateAdded -> R.string.book_detail_more_rule_field_date_added
    ShelfRuleField.DateUpdated -> R.string.book_detail_more_rule_field_date_updated
}

private fun opName(op: ShelfRuleOp): Int = when (op) {
    ShelfRuleOp.Is -> R.string.book_detail_more_rule_op_is
    ShelfRuleOp.IsNot -> R.string.book_detail_more_rule_op_is_not
    ShelfRuleOp.Contains -> R.string.book_detail_more_rule_op_contains
    ShelfRuleOp.StartsWith -> R.string.book_detail_more_rule_op_starts_with
    ShelfRuleOp.Gte -> R.string.book_detail_more_rule_op_gte
    ShelfRuleOp.Includes -> R.string.book_detail_more_rule_op_includes
    ShelfRuleOp.InLast -> R.string.book_detail_more_rule_op_in_last
    ShelfRuleOp.Between -> R.string.book_detail_more_rule_op_between
    ShelfRuleOp.Before -> R.string.book_detail_more_rule_op_before
    ShelfRuleOp.After -> R.string.book_detail_more_rule_op_after
}

private fun unitName(unit: Char): Int = when (unit) {
    'd' -> R.plurals.book_detail_more_rule_unit_days
    'w' -> R.plurals.book_detail_more_rule_unit_weeks
    'm' -> R.plurals.book_detail_more_rule_unit_months
    else -> R.plurals.book_detail_more_rule_unit_years
}

private fun statusName(value: String): Int? = when (value) {
    "unread" -> R.string.book_detail_more_rule_status_unread
    "reading" -> R.string.book_detail_more_rule_status_reading
    "finished" -> R.string.book_detail_more_rule_status_finished
    else -> null
}
