package com.omnibus.omnibus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalOmnibusPalette = staticCompositionLocalOf { OmnibusPalette.Atrium }

/** Whether the nearest [OmnibusTheme] is dark. The palette local defaults to the dark Atrium, so this does too. */
private val LocalOmnibusIsDark = staticCompositionLocalOf { true }

/**
 * Applies the Atrium palette to [content], both as [OmnibusTheme.palette] and mapped onto the
 * Material [ColorScheme] so stock Material components pick up the same colors.
 *
 * @param darkTheme Selects [OmnibusPalette.Atrium] when true, [OmnibusPalette.Light] otherwise.
 */
@Composable
fun OmnibusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) OmnibusPalette.Atrium else OmnibusPalette.Light

    CompositionLocalProvider(
        LocalOmnibusPalette provides palette,
        LocalOmnibusIsDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(darkTheme),
            typography = Typography,
            content = content
        )
    }
}

object OmnibusTheme {
    /** The palette provided by the nearest enclosing [OmnibusTheme]. */
    val palette: OmnibusPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalOmnibusPalette.current

    /** Whether the nearest [OmnibusTheme] is dark. */
    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalOmnibusIsDark.current
}

private fun OmnibusPalette.toColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = accentInk,
        secondary = accent,
        onSecondary = accentInk,
        tertiary = accent,
        onTertiary = accentInk,
        background = bg0,
        onBackground = ink0,
        surface = bg0,
        onSurface = ink0,
        surfaceVariant = bg1,
        onSurfaceVariant = ink2,
        outline = line,
        outlineVariant = line,
        error = bad,
        onError = accentInk,
    )
}
