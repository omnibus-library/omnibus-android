package com.omnibus.omnibus.feature.libraries.add

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.libraries.switcher.SwitcherAction

/** The "Add library" action (#371), shared by the switcher sheet and Manage libraries: the switcher's action row. */
@Composable
internal fun AddLibraryAction(
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) = SwitcherAction(
    icon = R.drawable.ic_reader_plus,
    label = stringResource(R.string.libraries_add_action),
    testTag = testTag,
    onClick = onClick,
    modifier = modifier,
)
