package com.omnibus.omnibus.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The app theme over its background. A preview has no ground of its own, so without this the dark variant of
 * `@PreviewLightDark` would draw light text on white.
 */
@Composable
fun Shot(content: @Composable () -> Unit) {
    OmnibusTheme {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
