package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing

/**
 * Shown for the moment it takes to read the saved server configuration at launch.
 *
 * Mirrors the iOS client's `LaunchView`: the stoat mark over a small spinner on the screen
 * background, which is also the system splash's ground so there is no flash between the two.
 */
@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val loadingDescription = stringResource(R.string.launch_loading)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg0)
            // The mark is decorative and the spinner has no label of its own, so without this
            // the launching state announces as a bare busy indicator.
            .clearAndSetSemantics { contentDescription = loadingDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.omnibus_stoat),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = palette.ink3,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
@PreviewLightDark
private fun LaunchScreenPreview() {
    OmnibusTheme {
        LaunchScreen()
    }
}
