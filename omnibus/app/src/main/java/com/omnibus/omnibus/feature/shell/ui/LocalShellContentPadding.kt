package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Bottom padding a shell entry gives its scroll container so its last row scrolls clear of the floating chrome (I-ARCH-2,
 * AND-LAY-3). Entries apply it; they never compute it. Zero outside the shell.
 */
val LocalShellContentPadding = compositionLocalOf { PaddingValues(0.dp) }
