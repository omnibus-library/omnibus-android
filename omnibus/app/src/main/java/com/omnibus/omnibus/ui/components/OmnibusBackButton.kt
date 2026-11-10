package com.omnibus.omnibus.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R

/**
 * The Material 3 back arrow for top app bars. It takes its tint from `LocalContentColor`, so the bar sets the color,
 * and it mirrors in RTL.
 *
 * @param onClick Called when the arrow is tapped.
 */
@Composable
fun OmnibusBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.navigation_back),
        )
    }
}
