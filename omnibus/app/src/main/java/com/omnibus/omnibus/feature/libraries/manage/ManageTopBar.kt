package com.omnibus.omnibus.feature.libraries.manage

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The width the Manage libraries screens' content is capped at, from 600dp up, centred. */
internal val ManageContentMaxWidth = 560.dp

/**
 * The top bar of the Manage libraries screens: a back arrow and a heading title.
 *
 * @param title The title, shown as a heading.
 * @param onBack Called when the back arrow is tapped.
 * @param titleTag The test tag for the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageTopBar(
    title: String,
    onBack: () -> Unit,
    titleTag: String,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    TopAppBar(
        title = {
            Text(
                title,
                style = uiTextStyle(22, FontWeight.SemiBold, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }.testTag(titleTag),
            )
        },
        navigationIcon = { OmnibusBackButton(onClick = onBack) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = palette.bg0,
            scrolledContainerColor = palette.bg0,
            navigationIconContentColor = palette.ink0,
            titleContentColor = palette.ink0,
        ),
        modifier = modifier,
    )
}
