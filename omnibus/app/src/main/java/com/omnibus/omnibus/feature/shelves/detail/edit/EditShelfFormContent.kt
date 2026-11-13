package com.omnibus.omnibus.feature.shelves.detail.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.feature.bookdetail.more.shelves.rememberRuleWords
import com.omnibus.omnibus.feature.bookdetail.more.shelves.smartShelfRuleChip
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags of the edit form's fields and sections, so UI tests can find them. */
internal object EditShelfTestTags {
    const val NAME = "edit-shelf-name"
    const val DESCRIPTION = "edit-shelf-description"
    const val MATCH_ALL = "edit-shelf-match-all"
    const val MATCH_ANY = "edit-shelf-match-any"
    const val ADD_CONDITION = "edit-shelf-add-condition"
    const val MATCH_COUNT = "edit-shelf-match-count"
    const val OFFLINE = "edit-shelf-offline"
    const val ERROR = "edit-shelf-error"
    const val SAVE = "edit-shelf-save"
    const val CANCEL = "edit-shelf-cancel"
    const val LOADING = "edit-shelf-loading"
}

/**
 * The edit form's fields: the name, description, visibility, and for a smart shelf its conditions and count. [centered]
 * only changes where a condition's value sits. Every change goes out through the callbacks.
 */
@Composable
internal fun EditShelfFormContent(
    state: EditShelfUIState,
    centered: Boolean,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onVisibility: (ShelfVisibility) -> Unit,
    onMatchMode: (ShelfMatchMode) -> Unit,
    onRuleField: (id: Int, ShelfRuleField) -> Unit,
    onRuleOp: (id: Int, ShelfRuleOp) -> Unit,
    onRuleValue: (id: Int, String) -> Unit,
    onRemoveRule: (id: Int) -> Unit,
    onAddRule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val words = rememberRuleWords()
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (!state.online) OfflineCard()

        OutlinedTextField(
            value = state.name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.shelf_edit_name)) },
            singleLine = true,
            colors = fieldColors(),
            shape = RoundedCornerShape(Spacing.md),
            modifier = Modifier.fillMaxWidth().testTag(EditShelfTestTags.NAME),
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = onDescription,
            label = { Text(stringResource(R.string.shelf_edit_description)) },
            enabled = state.loaded,
            minLines = 3,
            colors = fieldColors(),
            shape = RoundedCornerShape(Spacing.md),
            modifier = Modifier.fillMaxWidth().testTag(EditShelfTestTags.DESCRIPTION),
        )

        SectionHeading(stringResource(R.string.shelf_edit_visibility))
        VisibilityChoice(state.visibility, onVisibility)

        if (state.isSmart) {
            SectionHeading(stringResource(R.string.shelf_edit_conditions))
            if (!state.loaded) {
                CircularProgressIndicator(
                    color = palette.ink3,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp).testTag(EditShelfTestTags.LOADING),
                )
            } else {
                MatchModeChoice(state.matchMode, state.rulesLocked, onMatchMode)
                if (state.rulesLocked) {
                    state.lockedRules.forEach { rule ->
                        Text(
                            text = smartShelfRuleChip(rule, words, locale),
                            style = uiTextStyle(14.sp, FontWeight.Medium, palette.ink0),
                            modifier = Modifier.background(palette.bg2, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                } else {
                    state.rules.forEach { rule ->
                        RuleRow(
                            rule = rule,
                            words = words,
                            centered = centered,
                            onField = { onRuleField(rule.id, it) },
                            onOp = { onRuleOp(rule.id, it) },
                            onValue = { onRuleValue(rule.id, it) },
                            onRemove = { onRemoveRule(rule.id) },
                        )
                    }
                    TextButton(onClick = onAddRule, modifier = Modifier.testTag(EditShelfTestTags.ADD_CONDITION)) {
                        Text(stringResource(R.string.shelf_edit_add_condition), style = uiTextStyle(14.sp, FontWeight.Medium, palette.accent))
                    }
                    state.matchCount?.let { count ->
                        Text(
                            text = stringResource(R.string.shelf_edit_match_count, count.matched.toInt(), count.total.toInt()),
                            style = monoTextStyle(11.sp, palette.ink3),
                            modifier = Modifier.testTag(EditShelfTestTags.MATCH_COUNT),
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            Text(
                text = when (error) {
                    EditShelfError.NameTaken -> stringResource(R.string.shelf_edit_error_name_taken)
                    is EditShelfError.Server -> error.message ?: stringResource(R.string.shelf_edit_error_generic)
                    EditShelfError.Generic -> stringResource(R.string.shelf_edit_error_generic)
                },
                style = uiTextStyle(13.sp, FontWeight.Medium, palette.bad),
                modifier = Modifier.testTag(EditShelfTestTags.ERROR),
            )
        }
    }
}

/** A title over a section of the form, as the boards draw them. */
@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = displayTextStyle(22, OmnibusTheme.palette.ink0),
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

@Composable
private fun VisibilityChoice(visibility: ShelfVisibility, onVisibility: (ShelfVisibility) -> Unit) {
    val palette = OmnibusTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                ShelfVisibility.Private to R.string.shelf_edit_private,
                ShelfVisibility.Public to R.string.shelf_edit_public,
            ).forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = visibility == value,
                    onClick = { onVisibility(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                ) {
                    Text(stringResource(label))
                }
            }
        }
        Text(
            text = stringResource(
                if (visibility == ShelfVisibility.Public) R.string.shelf_edit_public_helper else R.string.shelf_edit_private_helper,
            ),
            style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink3),
        )
    }
}

@Composable
private fun MatchModeChoice(matchMode: ShelfMatchMode, locked: Boolean, onMatchMode: (ShelfMatchMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(
            Triple(ShelfMatchMode.All, R.string.shelf_edit_match_all, EditShelfTestTags.MATCH_ALL),
            Triple(ShelfMatchMode.Any, R.string.shelf_edit_match_any, EditShelfTestTags.MATCH_ANY),
        ).forEachIndexed { index, (value, label, tag) ->
            SegmentedButton(
                selected = matchMode == value,
                onClick = { onMatchMode(value) },
                enabled = !locked,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                modifier = Modifier.testTag(tag),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

/** The card that says the save waits for a connection. */
@Composable
private fun OfflineCard() {
    val palette = OmnibusTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bg2, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag(EditShelfTestTags.OFFLINE),
    ) {
        Icon(painter = painterResource(R.drawable.ic_wifi_off), contentDescription = null, tint = palette.accent)
        Text(
            text = stringResource(R.string.shelf_edit_offline),
            style = uiTextStyle(13.sp, FontWeight.Normal, palette.ink1),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The full-screen form's frame: a bar with close, the title and Save, then the form, scrollable. Used below 600dp.
 */
@Composable
internal fun EditShelfFullScreenFrame(
    canSave: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth().background(palette.bg0)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_reader_close),
                    contentDescription = stringResource(R.string.shelf_edit_close),
                    tint = palette.ink0,
                )
            }
            Text(
                text = stringResource(R.string.shelf_edit_title),
                style = displayTextStyle(22, palette.ink0),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            TextButton(onClick = onSave, enabled = canSave, modifier = Modifier.testTag(EditShelfTestTags.SAVE)) {
                Text(
                    stringResource(R.string.shelf_edit_save),
                    style = uiTextStyle(15.sp, FontWeight.SemiBold, if (canSave) palette.accent else palette.ink3),
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg),
        ) {
            content()
        }
    }
}

/**
 * The centered form's frame: a card with the title, the form, and Cancel and Save at the bottom right. Used from 600dp.
 */
@Composable
internal fun EditShelfCenteredFrame(
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .background(palette.bg1, RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.shelf_edit_title),
            style = displayTextStyle(22, palette.ink0),
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            content()
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(EditShelfTestTags.CANCEL)) {
                Text(stringResource(R.string.shelf_edit_cancel), style = uiTextStyle(14.sp, FontWeight.Medium, palette.ink1))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.bg0),
                modifier = Modifier.testTag(EditShelfTestTags.SAVE),
            ) {
                Text(stringResource(R.string.shelf_edit_save))
            }
        }
    }
}
