package com.omnibus.omnibus.feature.shelves.detail.edit

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.acceptedOps
import com.omnibus.omnibus.feature.bookdetail.more.shelves.RuleWords
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags of one condition row, so tests can find its chips, value and remove button. */
internal object RuleRowTestTags {
    const val FIELD = "rule-row-field"
    const val OP = "rule-row-op"
    const val VALUE = "rule-row-value"
    const val REMOVE = "rule-row-remove"
    const val FIELD_MENU = "rule-row-field-menu"
    const val OP_MENU = "rule-row-op-menu"
    const val VALUE_MENU = "rule-row-value-menu"
}

/**
 * One condition of a smart shelf: a field menu, an op menu, a value and a remove button. The op menu lists only the
 * ops the field accepts. The value is a menu when the field has a fixed set of values (see [fixedValuesOf]), else text.
 */
@Composable
internal fun RuleRow(
    rule: EditableRule,
    words: RuleWords,
    centered: Boolean,
    onField: (ShelfRuleField) -> Unit,
    onOp: (ShelfRuleOp) -> Unit,
    onValue: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val fieldText = fieldLabel(rule.field, words)
    val opText = stringResource(opLabelRes(rule.op))
    Column(
        modifier
            .fillMaxWidth()
            .background(palette.bg1, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipMenu(
                label = fieldText,
                description = stringResource(R.string.shelf_edit_field_description, fieldText),
                tag = RuleRowTestTags.FIELD,
                menuTag = RuleRowTestTags.FIELD_MENU,
                items = ShelfRuleField.entries.map { it to fieldLabel(it, words) },
                onSelect = onField,
            )
            ChipMenu(
                label = opText,
                description = stringResource(R.string.shelf_edit_op_description, opText),
                tag = RuleRowTestTags.OP,
                menuTag = RuleRowTestTags.OP_MENU,
                items = rule.field.acceptedOps.map { it to stringResource(opLabelRes(it)) },
                onSelect = onOp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove, modifier = Modifier.testTag(RuleRowTestTags.REMOVE)) {
                Icon(
                    painter = painterResource(R.drawable.ic_minus_circle),
                    contentDescription = stringResource(R.string.shelf_edit_remove_condition),
                    tint = palette.bad,
                )
            }
        }
        val fixed = fixedValuesOf(rule.field, rule.op)
        if (fixed != null) {
            ValueMenu(
                value = rule.value,
                options = fixed.map { it to valueLabel(rule.field, it, words) },
                onSelect = onValue,
            )
        } else {
            OutlinedTextField(
                value = rule.value,
                onValueChange = onValue,
                label = { Text(stringResource(R.string.shelf_edit_value)) },
                singleLine = true,
                colors = fieldColors(),
                shape = RoundedCornerShape(Spacing.md),
                modifier = Modifier.fillMaxWidth().testTag(RuleRowTestTags.VALUE),
            )
        }
    }
}

/**
 * The value of a condition whose values are a fixed set: a read-only field that opens a menu of [options]. A [value] the
 * menu does not offer is still shown, as it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueMenu(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.shelf_edit_value)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = fieldColors(),
            shape = RoundedCornerShape(Spacing.md),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(RuleRowTestTags.VALUE),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag(RuleRowTestTags.VALUE_MENU),
        ) {
            options.forEach { (token, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(token)
                    },
                )
            }
        }
    }
}

/** How a fixed [value] of [field] reads in the value menu: a status by its words, a format in capitals, a rating as is. */
private fun valueLabel(field: ShelfRuleField, value: String, words: RuleWords): String = when (field) {
    ShelfRuleField.Status -> words.status(value)?.replaceFirstChar { it.uppercase() } ?: value
    ShelfRuleField.Format -> value.uppercase()
    else -> value
}

/** A chip that opens a menu of [items]. Its semantics announce the chip's [description] as a dropdown. */
@Composable
private fun <T> ChipMenu(
    label: String,
    description: String,
    tag: String,
    menuTag: String,
    items: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    val palette = OmnibusTheme.palette
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(palette.bg2, CircleShape)
                .clickable(role = Role.DropdownList) { expanded = true }
                .testTag(tag)
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics { contentDescription = description; role = Role.DropdownList },
        ) {
            Text(text = label, style = uiTextStyle(14.sp, FontWeight.Medium, palette.ink0))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = palette.ink3,
                modifier = Modifier.padding(start = 4.dp).size(10.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag(menuTag),
        ) {
            items.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** A field's label as the menus show it: its rule word with the first letter capitalised. */
internal fun fieldLabel(field: ShelfRuleField, words: RuleWords): String =
    words.field(field).replaceFirstChar { it.uppercase() }

/** The plain label of an op, as the op menu lists it. */
@StringRes
internal fun opLabelRes(op: ShelfRuleOp?): Int = when (op) {
    ShelfRuleOp.Is, null -> R.string.shelf_edit_op_is
    ShelfRuleOp.IsNot -> R.string.shelf_edit_op_is_not
    ShelfRuleOp.Contains -> R.string.shelf_edit_op_contains
    ShelfRuleOp.StartsWith -> R.string.shelf_edit_op_starts_with
    ShelfRuleOp.Gte -> R.string.shelf_edit_op_gte
    ShelfRuleOp.Includes -> R.string.shelf_edit_op_includes
    ShelfRuleOp.InLast -> R.string.shelf_edit_op_in_last
    ShelfRuleOp.Between -> R.string.shelf_edit_op_between
    ShelfRuleOp.Before -> R.string.shelf_edit_op_before
    ShelfRuleOp.After -> R.string.shelf_edit_op_after
}

/** The text fields' colours: a filled bg1 field with no underline, its label in ink3. */
@Composable
internal fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = OmnibusTheme.palette.bg1,
    unfocusedContainerColor = OmnibusTheme.palette.bg1,
    disabledContainerColor = OmnibusTheme.palette.bg1,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedLabelColor = OmnibusTheme.palette.ink3,
    unfocusedLabelColor = OmnibusTheme.palette.ink3,
    focusedTextColor = OmnibusTheme.palette.ink0,
    unfocusedTextColor = OmnibusTheme.palette.ink0,
    cursorColor = OmnibusTheme.palette.accent,
)
