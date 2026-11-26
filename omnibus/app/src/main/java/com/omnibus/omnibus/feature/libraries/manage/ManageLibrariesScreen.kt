package com.omnibus.omnibus.feature.libraries.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.switcher.LibraryRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing

/** Test tags for the Manage libraries list. */
internal object ManageLibrariesTestTags {
    const val TITLE = "manage-libraries-title"
    const val LIST = "manage-libraries-list"
}

/**
 * Manage libraries: every saved library in the order they were added. Tapping one opens its detail.
 *
 * @param onBack Called when the back arrow is tapped.
 * @param onOpenLibrary Called with the library's id when its row is tapped.
 */
@Composable
fun ManageLibrariesScreen(
    onBack: () -> Unit,
    onOpenLibrary: (libraryId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManageLibrariesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    ManageLibrariesScreenContent(
        libraries = uiState.libraries,
        onBack = onBack,
        onOpenLibrary = onOpenLibrary,
        modifier = modifier,
    )
}

/**
 * The Manage libraries screen without its ViewModel, so tests and screenshots can render it with plain state.
 *
 * @param footer Shown after the list. #371 puts Add library here.
 */
@Composable
internal fun ManageLibrariesScreenContent(
    libraries: List<Library>,
    onBack: () -> Unit,
    onOpenLibrary: (libraryId: String) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxSize().background(palette.bg0)) {
        ManageTopBar(
            title = stringResource(R.string.libraries_manage_title),
            onBack = onBack,
            titleTag = ManageLibrariesTestTags.TITLE,
        )
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).testTag(ManageLibrariesTestTags.LIST),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            items(libraries, key = { it.id }) { library ->
                Column(Modifier.widthIn(max = ManageContentMaxWidth).fillMaxWidth().padding(horizontal = Spacing.screen)) {
                    LibraryRow(
                        library = library,
                        isCurrent = library.isActive,
                        onClick = { onOpenLibrary(library.id) },
                        showCheck = false,
                        trailing = {
                            Icon(
                                painterResource(R.drawable.ic_reader_chevron_right),
                                contentDescription = null,
                                tint = palette.ink2,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = palette.line)
                }
            }
            item {
                Box(Modifier.widthIn(max = ManageContentMaxWidth).fillMaxWidth()) {
                    footer()
                }
            }
        }
    }
}
