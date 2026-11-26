package com.omnibus.omnibus.feature.libraries.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.libraries.LibraryHost
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.remove.RemoveLibraryButton
import com.omnibus.omnibus.feature.libraries.remove.RemoveLibraryDialog
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the library detail screen. */
internal object LibraryDetailTestTags {
    const val TITLE = "library-detail-title"
    const val NAME_FIELD = "library-detail-name"
    const val ADDRESS = "library-detail-address"
    const val USERNAME = "library-detail-username"
}

/**
 * One library's detail: an editable name, and its address and username, which can't be changed here. The name is
 * saved when the reader leaves the screen.
 *
 * @param libraryId The library to show.
 * @param onBack Called when the reader leaves, by the back arrow or because the library is gone.
 */
@Composable
fun LibraryDetailScreen(
    libraryId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryDetailViewModel = hiltViewModel<LibraryDetailViewModel, LibraryDetailViewModel.Factory>(
        creationCallback = { it.create(libraryId) },
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    LibraryDetailScreenContent(
        uiState = uiState,
        onNameChange = viewModel::nameChanged,
        onLeave = viewModel::save,
        onBack = onBack,
        modifier = modifier,
        actions = { if (uiState.canRemove) RemoveLibraryButton(onClick = viewModel::removeClicked) },
    )
    val library = uiState.library
    if (library != null && !uiState.isMissing) {
        RemoveLibraryDialog(
            state = uiState.removeState,
            name = library.displayName(),
            onCancel = viewModel::cancelRemove,
            onRemove = viewModel::confirmRemove,
            onRemoveAnyway = viewModel::removeAnyway,
        )
    }
}

/**
 * The library detail screen without its ViewModel, so tests and screenshots can render it with plain state.
 *
 * @param onLeave Called when the reader leaves: the back arrow, the app stopping, or the screen leaving composition.
 * Saving is idempotent, so repeated calls are harmless.
 * @param onBack Called to leave the screen.
 * @param actions Shown under the fields: Remove library.
 */
@Composable
internal fun LibraryDetailScreenContent(
    uiState: LibraryDetailUIState,
    onNameChange: (String) -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    val currentOnLeave by rememberUpdatedState(onLeave)
    val currentOnBack by rememberUpdatedState(onBack)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) currentOnLeave() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Leaving composition saves too, for a pop by any route. Repeated saves are no-ops.
    DisposableEffect(Unit) { onDispose { currentOnLeave() } }
    LaunchedEffect(uiState.isMissing) {
        if (uiState.isMissing) currentOnBack()
    }

    Column(modifier.fillMaxSize().background(palette.bg0)) {
        ManageTopBar(
            title = uiState.title,
            onBack = {
                currentOnLeave()
                currentOnBack()
            },
            titleTag = LibraryDetailTestTags.TITLE,
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val library = uiState.library
            if (library != null) {
                Column(
                    Modifier
                        .widthIn(max = ManageContentMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    NameField(
                        name = uiState.name,
                        host = LibraryHost.of(library.address),
                        onNameChange = onNameChange,
                    )
                    // Plain rows rather than boxes, so only the name looks like something to edit.
                    Column(Modifier.padding(top = Spacing.sm)) {
                        HorizontalDivider(thickness = 0.5.dp, color = palette.line)
                        ReadOnlyRow(
                            label = stringResource(R.string.libraries_detail_address_label),
                            value = library.address,
                            testTag = LibraryDetailTestTags.ADDRESS,
                            mono = true,
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = palette.line)
                        library.username?.let { username ->
                            ReadOnlyRow(
                                label = stringResource(R.string.libraries_detail_username_label),
                                value = username,
                                testTag = LibraryDetailTestTags.USERNAME,
                                mono = false,
                            )
                            HorizontalDivider(thickness = 0.5.dp, color = palette.line)
                        }
                    }
                    actions()
                }
            }
        }
    }
}

/** The editable name: a labelled single-line field, with the hint under it. The host is its placeholder. */
@Composable
private fun NameField(name: String, host: String, onNameChange: (String) -> Unit) {
    val palette = OmnibusTheme.palette
    val label = stringResource(R.string.libraries_detail_name_label)
    val focusManager: FocusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = uiTextStyle(14, FontWeight.Normal, palette.ink2))
        OmnibusTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = host,
            modifier = Modifier
                .testTag(LibraryDetailTestTags.NAME_FIELD)
                .semantics { contentDescription = label },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        Text(
            stringResource(R.string.libraries_detail_name_hint),
            style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink3),
        )
    }
}

/**
 * A label over a value the reader can't edit, drawn as plain text with no field box. The label and value are one
 * TalkBack element.
 */
@Composable
private fun ReadOnlyRow(label: String, value: String, testTag: String, mono: Boolean) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = uiTextStyle(14, FontWeight.Normal, palette.ink2))
        Text(
            value,
            style = if (mono) monoTextStyle(15, palette.ink0) else uiTextStyle(16, FontWeight.Normal, palette.ink0),
        )
    }
}
