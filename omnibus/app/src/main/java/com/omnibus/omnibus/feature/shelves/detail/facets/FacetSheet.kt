package com.omnibus.omnibus.feature.shelves.detail.facets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.selection.toggleable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.FilterMode
import com.omnibus.omnibus.data.shelves.MAX_FILTER_VALUES
import com.omnibus.omnibus.ui.components.StateActionButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

/** Test tags for the facet sheet, so UI tests can find its controls and options. */
internal object FacetSheetTestTags {
    const val CLOSE = "facet-sheet-close"
    const val INCLUDE = "facet-sheet-include"
    const val EXCLUDE = "facet-sheet-exclude"
    const val APPLY = "facet-sheet-apply"
    const val CANCEL = "facet-sheet-cancel"
    const val RETRY = "facet-sheet-retry"
    const val LOADING = "facet-sheet-loading"

    fun option(value: String) = "facet-sheet-option-$value"
}

/**
 * A facet's sheet: its options with counts, an Includes or Excludes choice, and Cancel or Apply. The options load when
 * the sheet opens; [onApply] reports the one clause the sheet built.
 *
 * @param initial The clause the facet already has, which seeds the sheet, or null when it is unset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FacetSheet(
    field: FilterField,
    initial: FilterClause?,
    onApply: (FilterClause) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel = hiltViewModel<FacetOptionsViewModel, FacetOptionsViewModel.Factory>(
        key = "facet-${field.wire}",
        creationCallback = { it.create(field) },
    )
    val state by viewModel.uiState.collectAsState()
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by rememberSaveable { mutableStateOf(initial?.mode ?: FilterMode.Include) }
    var picked by rememberSaveable { mutableStateOf(ArrayList(initial?.values.orEmpty())) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = palette.bg0,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        FacetSheetContent(
            field = field,
            state = state,
            mode = mode,
            picked = picked,
            onModeChange = { mode = it },
            onToggle = { picked = ArrayList(toggleValue(picked, it)) },
            onRetry = viewModel::retry,
            onApply = { clause ->
                onApply(clause)
                onDismiss()
            },
            onCancel = onDismiss,
            onClose = onDismiss,
        )
    }
}

/**
 * The facet sheet's body, without its modal, on hand-built state. Apply reports one clause of [field], [mode] and
 * [picked].
 */
@Composable
internal fun FacetSheetContent(
    field: FilterField,
    state: FacetOptionsState,
    mode: FilterMode,
    picked: List<String>,
    onModeChange: (FilterMode) -> Unit,
    onToggle: (String) -> Unit,
    onRetry: () -> Unit,
    onApply: (FilterClause) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(fieldNameRes(field)),
                style = displayTextStyle(28, palette.ink0),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_reader_close),
                    contentDescription = stringResource(R.string.shelf_detail_facet_close),
                    tint = palette.ink2,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = Spacing.md)) {
            listOf(
                Triple(FilterMode.Include, R.string.shelf_detail_facet_includes, FacetSheetTestTags.INCLUDE),
                Triple(FilterMode.Exclude, R.string.shelf_detail_facet_excludes, FacetSheetTestTags.EXCLUDE),
            ).forEachIndexed { index, (segment, label, tag) ->
                SegmentedButton(
                    selected = mode == segment,
                    onClick = { onModeChange(segment) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = palette.bg2,
                        activeContentColor = palette.ink0,
                        activeBorderColor = palette.ink3,
                        inactiveContainerColor = palette.bg0,
                        inactiveContentColor = palette.ink2,
                        inactiveBorderColor = palette.ink3,
                    ),
                    modifier = Modifier.testTag(tag),
                ) {
                    Text(text = stringResource(label), style = uiTextStyle(14.sp, FontWeight.Medium, palette.ink0))
                }
            }
        }

        when (state) {
            FacetOptionsState.Loading -> Box(
                Modifier.fillMaxWidth().height(180.dp).testTag(FacetSheetTestTags.LOADING),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = palette.ink3, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }

            FacetOptionsState.Failed -> Column(
                Modifier.fillMaxWidth().height(180.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.shelf_detail_facet_error, stringResource(fieldPluralRes(field))),
                    style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.bad),
                )
                Text(
                    text = stringResource(R.string.shelf_detail_facet_error_detail),
                    style = uiTextStyle(13.sp, FontWeight.Normal, palette.ink3),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                StateActionButton(
                    label = stringResource(R.string.shelf_detail_facet_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(FacetSheetTestTags.RETRY),
                )
            }

            is FacetOptionsState.Loaded -> LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(state.options, key = { it.value }) { option ->
                    val checked = picked.any { it.equals(option.value, ignoreCase = true) }
                    val enabled = checked || picked.size < MAX_FILTER_VALUES
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(option.value) },
                            )
                            .testTag(FacetSheetTestTags.option(option.value))
                            .padding(vertical = 8.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = enabled,
                            colors = CheckboxDefaults.colors(checkedColor = palette.accent, uncheckedColor = palette.ink3),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = option.label,
                            style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink0),
                            modifier = Modifier.weight(1f),
                        )
                        option.count?.let {
                            Text(text = it.toString(), style = monoTextStyle(11.sp, palette.ink3))
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.testTag(FacetSheetTestTags.CANCEL)) {
                Text(text = stringResource(R.string.shelf_detail_facet_cancel), style = uiTextStyle(14.sp, FontWeight.Medium, palette.ink1))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onApply(FilterClause(field, mode, picked)) },
                enabled = state is FacetOptionsState.Loaded,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.bg0),
                modifier = Modifier.testTag(FacetSheetTestTags.APPLY),
            ) {
                Text(text = stringResource(R.string.shelf_detail_facet_apply))
            }
        }
    }
}
