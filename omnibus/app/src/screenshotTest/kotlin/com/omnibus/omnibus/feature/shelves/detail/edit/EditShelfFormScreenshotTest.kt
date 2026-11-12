package com.omnibus.omnibus.feature.shelves.detail.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.RuleMatchCount
import com.omnibus.omnibus.screenshot.Shot

/** The edit form, full screen, for a smart shelf with one condition and its match count, in the light palette. */
@PreviewTest
@Preview(name = "Light", widthDp = 390, heightDp = 900)
@Composable
fun EditShelfFormSmartLight() {
    Shot(darkTheme = false) {
        EditShelfFullScreenFrame(canSave = true, onClose = {}, onSave = {}) {
            EditShelfFormContent(
                state = EditShelfUIState(
                    name = "Weekend reads",
                    description = "Long afternoons",
                    visibility = ShelfVisibility.Public,
                    matchMode = ShelfMatchMode.All,
                    rules = listOf(EditableRule(id = 0, field = ShelfRuleField.Rating, op = ShelfRuleOp.Is, value = "5")),
                    isSmart = true,
                    loaded = true,
                    online = true,
                    matchCount = RuleMatchCount(matched = 14, total = 212),
                ),
                centered = false,
                onName = {},
                onDescription = {},
                onVisibility = {},
                onMatchMode = {},
                onRuleField = { _, _ -> },
                onRuleOp = { _, _ -> },
                onRuleValue = { _, _ -> },
                onRemoveRule = {},
                onAddRule = {},
            )
        }
    }
}
