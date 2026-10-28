package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Atrium palette shared with the iOS and web clients.
 *
 * Token names mirror the iOS `Palette` (and the `atrium.css` custom properties) so a value can be
 * traced across platforms. Values are sRGB conversions of the OKLCH originals.
 *
 * @property bg0 Screen background.
 * @property bg1 Raised surfaces such as text fields.
 * @property line Hairline borders.
 * @property ink0 Primary text.
 * @property ink2 Secondary text and field labels.
 * @property ink3 Tertiary text, hints, and placeholders.
 * @property accent Fill for primary actions.
 * @property accentInk Content drawn on top of [accent].
 * @property bad Error text and icons.
 * @property bg2 Raised controls, such as the masthead add button.
 * @property line2 Quieter hairlines and ghost plates.
 * @property ink1 Between [ink0] and [ink2], for secondary emphasis.
 * @property warn Offline and caution tint.
 */
data class OmnibusPalette(
    val bg0: Color,
    val bg1: Color,
    val line: Color,
    val ink0: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
    val accentInk: Color,
    val bad: Color,
    val bg2: Color,
    val line2: Color,
    val ink1: Color,
    val warn: Color,
) {
    companion object {
        /** The default dark theme. */
        val Atrium = OmnibusPalette(
            bg0 = Color(0xFF0A0806),
            bg1 = Color(0xFF13100D),
            line = Color(0xA6312D28),
            ink0 = Color(0xFFF7F5F1),
            ink2 = Color(0xFF8B857F),
            ink3 = Color(0xFF625D56),
            accent = Color(0xFFF0A556),
            accentInk = Color(0xFF130B05),
            bad = Color(0xFFF97770),
            bg2 = Oklch(0.215, 0.010, 70.0).toColor(),
            line2 = Oklch(0.30, 0.010, 70.0, alpha = 0.30).toColor(),
            ink1 = Oklch(0.82, 0.010, 75.0).toColor(),
            warn = Oklch(0.78, 0.13, 75.0).toColor(),
        )

        val Light = OmnibusPalette(
            bg0 = Color(0xFFFBFAF8),
            bg1 = Color(0xFFF5F3F0),
            line = Color(0x334B4742),
            ink0 = Color(0xFF110F0D),
            ink2 = Color(0xFF6D6863),
            ink3 = Color(0xFF67625D),
            accent = Color(0xFFF0A556),
            accentInk = Color(0xFF130B05),
            bad = Color(0xFFF97770),
            bg2 = Oklch(0.940, 0.006, 75.0).toColor(),
            line2 = Oklch(0.40, 0.010, 70.0, alpha = 0.10).toColor(),
            ink1 = Oklch(0.36, 0.008, 70.0).toColor(),
            warn = Oklch(0.78, 0.13, 75.0).toColor(),
        )
    }
}
