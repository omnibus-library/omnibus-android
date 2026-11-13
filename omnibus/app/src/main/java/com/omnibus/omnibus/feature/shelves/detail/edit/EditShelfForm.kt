package com.omnibus.omnibus.feature.shelves.detail.edit

import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfUpdate
import com.omnibus.omnibus.data.shelves.acceptedOps
import com.omnibus.omnibus.data.library.ShelfVisibility

/**
 * One condition as the editor holds it. [id] is stable across edits, so a row keeps its place while its neighbours change.
 */
data class EditableRule(val id: Int, val field: ShelfRuleField, val op: ShelfRuleOp, val value: String)

/**
 * The rule with [newField], its op kept when the field accepts it, else reset to the field's first accepted op. The value
 * is kept only if it still fits (see [fixedValuesOf]).
 */
fun EditableRule.withField(newField: ShelfRuleField): EditableRule =
    copy(field = newField, op = if (op in newField.acceptedOps) op else newField.acceptedOps.first())
        .fittingValueFrom(this)

/** The rule with [newOp], its value kept only if it still fits (see [fixedValuesOf]). */
fun EditableRule.withOp(newOp: ShelfRuleOp): EditableRule = copy(op = newOp).fittingValueFrom(this)

/** A new condition as "Add condition" makes it: Tag, is, empty. */
fun newRule(id: Int): EditableRule = EditableRule(id, ShelfRuleField.Tag, ShelfRuleOp.Is, "")

/** The conditions the server can take: a non-blank value, trimmed, with an op the field accepts. */
fun List<EditableRule>.valid(): List<ShelfRule> = mapNotNull { rule ->
    val value = rule.value.trim()
    if (value.isBlank() || rule.op !in rule.field.acceptedOps) null else ShelfRule(rule.field, rule.op, value)
}

/**
 * Whether Save is enabled: online, not already saving, a non-blank name, and for a smart shelf a fresh read that has
 * landed with at least one valid rule. A locked smart shelf is saved without its rules, so it needs no valid rule.
 */
fun canSave(
    online: Boolean,
    saving: Boolean,
    name: String,
    isSmart: Boolean,
    loaded: Boolean,
    rules: List<EditableRule>,
    rulesLocked: Boolean,
): Boolean = online && !saving && name.isNotBlank() &&
    (!isSmart || (loaded && (rulesLocked || rules.valid().isNotEmpty())))

/**
 * The update Save sends. The name and visibility always go. The description goes only once the fresh read has landed,
 * so a cached value is never written back. For a smart shelf the match mode and valid rules go too, unless the shelf
 * has rules the app can't edit: those are left as the server has them, so none is dropped by a save.
 */
fun updateOf(
    name: String,
    description: String,
    visibility: ShelfVisibility,
    matchMode: ShelfMatchMode,
    rules: List<EditableRule>,
    isSmart: Boolean,
    loaded: Boolean,
    rulesLocked: Boolean,
): ShelfUpdate {
    val sendRules = isSmart && !rulesLocked
    return ShelfUpdate(
        name = name.trim(),
        description = if (loaded) description.trim() else null,
        visibility = visibility,
        matchMode = if (sendRules) matchMode else null,
        rules = if (sendRules) rules.valid() else null,
    )
}
