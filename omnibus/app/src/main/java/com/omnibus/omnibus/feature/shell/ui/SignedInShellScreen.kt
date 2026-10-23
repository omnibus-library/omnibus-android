package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.serialization.Serializable

@Serializable
data object SignedInShellRoute : NavKey

/**
 * Placeholder for the signed-in shell until H00-48's tab shell.
 */
@Composable
fun SignedInShellScreen(modifier: Modifier = Modifier) {
    SignedInShellContent(modifier)
}

@Composable
private fun SignedInShellContent(modifier: Modifier) {
    val palette = OmnibusTheme.palette

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg0),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.shell_placeholder),
            style = uiTextStyle(16, FontWeight.Normal, palette.ink0),
        )
    }
}

@Composable
@PreviewLightDark
private fun SignedInShellScreenPreview() {
    OmnibusTheme {
        SignedInShellScreen()
    }
}
