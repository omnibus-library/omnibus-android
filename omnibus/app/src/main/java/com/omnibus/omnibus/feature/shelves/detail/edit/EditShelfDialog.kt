package com.omnibus.omnibus.feature.shelves.detail.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The width from which the edit form is a centered dialog rather than full screen. It is the shell's own 600 dp rail
 * breakpoint (`ShellLayout.kt`), so the editor and the navigation change together.
 */
internal val EDIT_DIALOG_CENTERED_MIN_WIDTH: Dp = 600.dp

/**
 * The edit shelf dialog for [seed]: full screen below [EDIT_DIALOG_CENTERED_MIN_WIDTH], a centered card from it. It closes
 * itself once a save succeeds, with [onSaved]. Each [session] has its own form: the view model outlives the dialog in the
 * screen's store, so without it a form that was saved would report itself saved again the next time the dialog opens, and
 * close at once.
 */
@Composable
internal fun EditShelfDialog(
    seed: Shelf,
    session: Int,
    centered: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel = hiltViewModel<EditShelfViewModel, EditShelfViewModel.Factory>(
        key = "edit-shelf-${seed.id}-$session",
        creationCallback = { it.create(seed) },
    )
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    val form: @Composable () -> Unit = {
        EditShelfFormContent(
            state = state,
            centered = centered,
            onName = viewModel::nameChanged,
            onDescription = viewModel::descriptionChanged,
            onVisibility = viewModel::visibilityChanged,
            onMatchMode = viewModel::matchModeChanged,
            onRuleField = viewModel::ruleFieldChanged,
            onRuleOp = viewModel::ruleOpChanged,
            onRuleValue = viewModel::ruleValueChanged,
            onRemoveRule = viewModel::removeRule,
            onAddRule = viewModel::addRule,
        )
    }
    if (centered) {
        Dialog(onDismissRequest = onDismiss) {
            EditShelfCenteredFrame(
                canSave = state.canSave,
                onCancel = onDismiss,
                onSave = viewModel::save,
                content = form,
            )
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = OmnibusTheme.palette.bg0) {
                // The dialog draws edge to edge, so the bars, the cutout and the keyboard are kept clear here.
                Column(Modifier.safeDrawingPadding()) {
                    EditShelfFullScreenFrame(
                        canSave = state.canSave,
                        onClose = onDismiss,
                        onSave = viewModel::save,
                        content = form,
                    )
                }
            }
        }
    }
}
