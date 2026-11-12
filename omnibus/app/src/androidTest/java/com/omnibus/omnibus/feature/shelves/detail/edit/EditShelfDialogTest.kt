package com.omnibus.omnibus.feature.shelves.detail.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.feature.bookdetail.more.shelves.rememberRuleWords
import com.omnibus.omnibus.feature.shelves.detail.edit.EditShelfFormContent
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The edit form's frames, fields, offline card, and rule rows, on hand-built state. Its look is covered by screenshot tests. */
class EditShelfDialogTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue232_tc34_theFullScreenFormHasCloseTitleAndSaveInItsBar() {
        var closed = 0
        var saved = 0
        setFullScreen(state = smartState(), onClose = { closed++ }, onSave = { saved++ })

        rule.onNodeWithContentDescription(string(R.string.shelf_edit_close)).performClick()
        rule.onNodeWithText(string(R.string.shelf_edit_title)).assertIsDisplayed()
        rule.onNodeWithTag(EditShelfTestTags.SAVE).performClick()

        assertEquals(1, closed)
        assertEquals(1, saved)
    }

    @Test
    fun issue232_tc34_theCenteredFormHasCancelAndSaveButtons() {
        var cancelled = 0
        var saved = 0
        setCentered(state = smartState(), onCancel = { cancelled++ }, onSave = { saved++ })

        rule.onNodeWithTag(EditShelfTestTags.CANCEL).performClick()
        rule.onNodeWithTag(EditShelfTestTags.SAVE).performClick()

        assertEquals(1, cancelled)
        assertEquals(1, saved)
    }

    @Test
    fun issue232_tc34_offlineShowsTheCardAndDisablesSave() {
        setFullScreen(state = smartState().copy(online = false))

        rule.onNodeWithTag(EditShelfTestTags.OFFLINE).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.shelf_edit_offline)).assertIsDisplayed()
        rule.onNodeWithTag(EditShelfTestTags.SAVE).assertIsNotEnabled()
    }

    @Test
    fun issue232_tc34_saveIsEnabledWhenTheFormCanSave() {
        setFullScreen(state = smartState())

        rule.onNodeWithTag(EditShelfTestTags.SAVE).assertIsEnabled()
    }

    @Test
    fun issue232_tc35_theFieldAndOpChipsAnnounceTheirCondition() {
        setRuleRow(EditableRule(id = 1, field = ShelfRuleField.Rating, op = ShelfRuleOp.Is, value = "5"))

        rule.onNodeWithTag(RuleRowTestTags.FIELD).assert(hasContentDescriptionExactly("Condition field, Rating"))
        rule.onNodeWithTag(RuleRowTestTags.OP).assert(hasContentDescriptionExactly("Condition operator, is"))
    }

    @Test
    fun issue232_tc35_removeCallsBackForTheCondition() {
        var removed = 0
        setRuleRow(
            EditableRule(id = 1, field = ShelfRuleField.Rating, op = ShelfRuleOp.Is, value = "5"),
            onRemove = { removed++ },
        )

        rule.onNodeWithTag(RuleRowTestTags.REMOVE).assert(hasContentDescriptionExactly(string(R.string.shelf_edit_remove_condition)))
        rule.onNodeWithTag(RuleRowTestTags.REMOVE).performClick()

        assertEquals(1, removed)
    }

    @Test
    fun issue232_tc35_theOpMenuListsOnlyTheOpsTheFieldAccepts() {
        setRuleRow(EditableRule(id = 1, field = ShelfRuleField.Rating, op = ShelfRuleOp.Is, value = "5"))

        rule.onNodeWithTag(RuleRowTestTags.OP).performClick()

        rule.onNodeWithText(string(R.string.shelf_edit_op_gte)).assertIsDisplayed()
        rule.onAllNodesWithText(string(R.string.shelf_edit_op_contains)).assertCountEquals(0)
    }

    private fun smartState() = EditShelfUIState(
        name = "Weekend reads",
        visibility = ShelfVisibility.Public,
        isSmart = true,
        loaded = true,
        online = true,
        matchMode = ShelfMatchMode.All,
        rules = listOf(EditableRule(id = 0, field = ShelfRuleField.Rating, op = ShelfRuleOp.Is, value = "5")),
    )

    private fun setFullScreen(
        state: EditShelfUIState,
        onClose: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    EditShelfFullScreenFrame(canSave = state.canSave, onClose = onClose, onSave = onSave) {
                        EditShelfFormHost(state, centered = false)
                    }
                }
            }
        }
    }

    private fun setCentered(
        state: EditShelfUIState,
        onCancel: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    EditShelfCenteredFrame(canSave = state.canSave, onCancel = onCancel, onSave = onSave) {
                        EditShelfFormHost(state, centered = true)
                    }
                }
            }
        }
    }

    private fun setRuleRow(
        rule: EditableRule,
        onRemove: () -> Unit = {},
    ) {
        this.rule.setContent {
            OmnibusTheme(darkTheme = true) {
                RuleRow(
                    rule = rule,
                    words = rememberRuleWords(),
                    centered = false,
                    onField = {},
                    onOp = {},
                    onValue = {},
                    onRemove = onRemove,
                )
            }
        }
    }

    private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}

/** The form with no-op callbacks, holding no state of its own: the test's state is passed in. */
@Composable
private fun EditShelfFormHost(state: EditShelfUIState, centered: Boolean) {
    EditShelfFormContent(
        state = state,
        centered = centered,
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
