package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** Test tags for the settings sheet's controls. */
internal object ReaderSettingsSheetTags {
    const val SHEET = "reader_settings_sheet"
    const val DONE = "reader_settings_done"
    const val AA_PREVIEW = "reader_settings_aa"
    const val SIZE_VALUE = "reader_settings_size_value"
    const val SIZE_DECREASE = "reader_settings_size_decrease"
    const val SIZE_INCREASE = "reader_settings_size_increase"
    const val LINE_HEIGHT_VALUE = "reader_settings_line_height_value"
    const val LINE_HEIGHT_DECREASE = "reader_settings_line_height_decrease"
    const val LINE_HEIGHT_INCREASE = "reader_settings_line_height_increase"
    const val JUSTIFY = "reader_settings_justify"

    fun swatch(theme: ReaderTheme) = "reader_settings_swatch_${theme.token}"
    fun typeface(face: ReaderTypeface) = "reader_settings_typeface_${face.token}"
    fun margins(margins: ReaderMargins) = "reader_settings_margins_${margins.token}"
}

/** Presentation constants for the settings sheet. */
internal object ReaderSettingsSheetDefaults {
    /** No dimming, so the reader can judge a change on the live page behind the sheet. */
    val ScrimColor = Color.Transparent
}

/**
 * The reader's settings sheet: page theme, typeface, size, line height, margins and justification.
 * Stateless apart from its sheet state. Each control reports the new settings through
 * [onSettingsChanged], and the caller owns the value. The sheet takes the app theme, never the
 * reading theme, so it is composed inside `OmnibusTheme()`.
 *
 * @param accent the open book's accent, for the selected states and the justify switch.
 * @param onDismiss called when the sheet is closed with Done, a swipe, or a tap outside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    accent: BookAccent,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .border(0.5.dp, palette.line, shape),
        shape = shape,
        containerColor = palette.bg0,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = ReaderSettingsSheetDefaults.ScrimColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.line2) },
    ) {
        ReaderSettingsSheetContent(
            settings = settings,
            accent = accent,
            onSettingsChanged = onSettingsChanged,
            onDone = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            },
            // Sizes to the controls, capped like the Contents sheet so a strip of page stays above
            // it on a short window; the controls scroll beyond the cap.
            modifier = Modifier.layout { measurable, constraints ->
                val maxHeight = if (constraints.hasBoundedHeight) {
                    (constraints.maxHeight * ContentsSheetHeightFraction).toInt()
                } else {
                    constraints.maxHeight
                }
                val placeable = measurable.measure(
                    constraints.copy(minHeight = minOf(constraints.minHeight, maxHeight), maxHeight = maxHeight),
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
        )
    }
}

/** The sheet's body, without the sheet, so it can be previewed. */
@Composable
internal fun ReaderSettingsSheetContent(
    settings: ReaderSettings,
    accent: BookAccent,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ReaderSettingsSheetTags.SHEET)
            .background(palette.bg0)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        SheetHeader(onDone = onDone)
        PageGroup(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
        TypeGroup(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
        LayoutGroup(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
    }
}

@Composable
private fun SheetHeader(onDone: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reader_settings_title),
            modifier = Modifier.weight(1f),
            style = displayTextStyle(28, palette.ink0),
        )
        TextButton(
            onClick = onDone,
            modifier = Modifier.testTag(ReaderSettingsSheetTags.DONE).heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_done),
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    val palette = OmnibusTheme.palette
    Text(
        text = text.uppercase(),
        style = uiTextStyle(11, FontWeight.SemiBold, palette.ink3).copy(letterSpacing = 1.sp),
    )
}

@Composable
private fun PageGroup(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GroupLabel(stringResource(R.string.reader_settings_group_page))
        Row(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            listOf(ReaderTheme.Light, ReaderTheme.Sepia, ReaderTheme.Dark, ReaderTheme.Black).forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = settings.theme == theme,
                    accent = accent,
                    onClick = { if (settings.theme != theme) onSettingsChanged(settings.copy(theme = theme)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A page in [theme]'s ground with four text-line bars in its ink, labelled beneath. */
@Composable
private fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    accent: BookAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val ink = readerSwatchInk(theme)
    val pageShape = RoundedCornerShape(Radius.md)
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(ReaderSettingsSheetTags.swatch(theme)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(pageShape)
                .background(Color(theme.groundArgb))
                .border(
                    width = if (selected) 2.dp else 0.75.dp,
                    color = if (selected) accent.tint else palette.line,
                    shape = pageShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                listOf(1.0f, 0.86f, 0.94f, 0.6f).forEach { fraction ->
                    Box(
                        Modifier
                            .width(30.dp * fraction)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.6f)),
                    )
                }
            }
        }
        Text(
            text = stringResource(swatchLabel(theme)),
            style = uiTextStyle(
                11,
                if (selected) FontWeight.SemiBold else FontWeight.Normal,
                if (selected) palette.ink0 else palette.ink3,
            ),
        )
    }
}

@Composable
private fun TypeGroup(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    val faces = ReaderTypeface.entries
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GroupLabel(stringResource(R.string.reader_settings_group_type))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            faces.chunked(3).forEach { rowFaces ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowFaces.forEach { face ->
                        TypefaceCapsule(
                            face = face,
                            selected = settings.typeface == face,
                            accent = accent,
                            onClick = {
                                if (settings.typeface != face) onSettingsChanged(settings.copy(typeface = face))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(palette.bg1)
                .border(0.5.dp, palette.line, RoundedCornerShape(Radius.lg)),
        ) {
            SizeRow(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.line2))
            LineHeightRow(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
        }
    }
}

/** One typeface capsule, labelled in the UI face. Filled with the accent when selected. */
@Composable
private fun TypefaceCapsule(
    face: ReaderTypeface,
    selected: Boolean,
    accent: BookAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(ReaderSettingsSheetTags.typeface(face))
            .clip(CircleShape)
            .background(if (selected) accent.tint else palette.bg2.copy(alpha = 0.7f))
            .padding(vertical = 7.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(typefaceLabel(face)),
            style = uiTextStyle(
                13,
                if (selected) FontWeight.SemiBold else FontWeight.Medium,
                if (selected) accent.onTint else palette.ink2,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SizeRow(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_settings_size),
            modifier = Modifier.weight(1f),
            style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
        )
        // The tag sits on the box, so the decorative text can clear its own semantics without losing it.
        Box(
            Modifier
                .width(56.dp)
                .testTag(ReaderSettingsSheetTags.AA_PREVIEW),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = stringResource(R.string.reader_settings_aa),
                modifier = Modifier.clearAndSetSemantics {},
                style = displayTextStyle(settings.fontSize, palette.ink1),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Text(
            text = settings.fontSize.toString(),
            modifier = Modifier.testTag(ReaderSettingsSheetTags.SIZE_VALUE).semantics {
                stateDescription = "${settings.fontSize} point"
            },
            style = monoTextStyle(12, palette.ink2),
        )
        Stepper(
            iconRes = R.drawable.ic_reader_minus,
            accent = accent,
            description = stringResource(R.string.reader_settings_size_decrease),
            enabled = settings.canDecreaseFontSize,
            onClick = { onSettingsChanged(settings.withFontSizeStep(-1)) },
            tag = ReaderSettingsSheetTags.SIZE_DECREASE,
        )
        Stepper(
            iconRes = R.drawable.ic_reader_plus,
            accent = accent,
            description = stringResource(R.string.reader_settings_size_increase),
            enabled = settings.canIncreaseFontSize,
            onClick = { onSettingsChanged(settings.withFontSizeStep(+1)) },
            tag = ReaderSettingsSheetTags.SIZE_INCREASE,
        )
    }
}

@Composable
private fun LineHeightRow(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_settings_line_height),
            modifier = Modifier.weight(1f),
            style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
        )
        Text(
            // Locale.ROOT keeps the decimal point, so 1.6 never reads as "1,6".
            text = String.format(Locale.ROOT, "%.1f", settings.lineHeight),
            modifier = Modifier.testTag(ReaderSettingsSheetTags.LINE_HEIGHT_VALUE),
            style = monoTextStyle(12, palette.ink2),
        )
        Stepper(
            iconRes = R.drawable.ic_reader_minus,
            accent = accent,
            description = stringResource(R.string.reader_settings_line_height_decrease),
            enabled = settings.canDecreaseLineHeight,
            onClick = { onSettingsChanged(settings.withLineHeightStep(-1)) },
            tag = ReaderSettingsSheetTags.LINE_HEIGHT_DECREASE,
        )
        Stepper(
            iconRes = R.drawable.ic_reader_plus,
            accent = accent,
            description = stringResource(R.string.reader_settings_line_height_increase),
            enabled = settings.canIncreaseLineHeight,
            onClick = { onSettingsChanged(settings.withLineHeightStep(+1)) },
            tag = ReaderSettingsSheetTags.LINE_HEIGHT_INCREASE,
        )
    }
}

/**
 * A round minus or plus. The touch target is 48dp around a 32dp circle. The glyph is the book's accent
 * on the dark app theme and the ink on the light one, and muted when disabled.
 */
@Composable
private fun Stepper(
    iconRes: Int,
    accent: BookAccent,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val palette = OmnibusTheme.palette
    val tint = when {
        !enabled -> palette.ink3
        palette == OmnibusPalette.Atrium -> accent.tint
        else -> palette.ink0
    }
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(palette.bg2, CircleShape)
                .border(0.5.dp, palette.line2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun LayoutGroup(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.lg)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GroupLabel(stringResource(R.string.reader_settings_group_layout))
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .clip(CircleShape)
                .background(palette.bg2.copy(alpha = 0.7f))
                .padding(3.dp),
        ) {
            ReaderMargins.entries.forEach { margins ->
                val selected = settings.margins == margins
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!selected) onSettingsChanged(settings.copy(margins = margins))
                            },
                        )
                        .testTag(ReaderSettingsSheetTags.margins(margins))
                        .clip(CircleShape)
                        .background(if (selected) accent.tint else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(marginsLabel(margins)),
                        style = uiTextStyle(
                            14,
                            if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            if (selected) accent.onTint else palette.ink2,
                        ),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.bg1)
                .border(0.5.dp, palette.line, shape)
                .toggleable(
                    value = settings.justify,
                    role = Role.Switch,
                    onValueChange = { onSettingsChanged(settings.copy(justify = it)) },
                )
                .testTag(ReaderSettingsSheetTags.JUSTIFY)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_settings_justify),
                modifier = Modifier.weight(1f),
                style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
            )
            Switch(
                checked = settings.justify,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent.tint,
                    checkedThumbColor = accent.onTint,
                ),
            )
        }
    }
}

private fun swatchLabel(theme: ReaderTheme): Int = when (theme) {
    ReaderTheme.Light -> R.string.reader_settings_theme_light
    ReaderTheme.Sepia -> R.string.reader_settings_theme_sepia
    ReaderTheme.Dark -> R.string.reader_settings_theme_dark
    ReaderTheme.Black -> R.string.reader_settings_theme_black
}

private fun typefaceLabel(face: ReaderTypeface): Int = when (face) {
    ReaderTypeface.Original -> R.string.reader_settings_typeface_original
    ReaderTypeface.Editorial -> R.string.reader_settings_typeface_editorial
    ReaderTypeface.Classic -> R.string.reader_settings_typeface_classic
    ReaderTypeface.Modern -> R.string.reader_settings_typeface_modern
    ReaderTypeface.Sans -> R.string.reader_settings_typeface_sans
    ReaderTypeface.Mono -> R.string.reader_settings_typeface_mono
}

private fun marginsLabel(margins: ReaderMargins): Int = when (margins) {
    ReaderMargins.Narrow -> R.string.reader_settings_margins_narrow
    ReaderMargins.Normal -> R.string.reader_settings_margins_normal
    ReaderMargins.Wide -> R.string.reader_settings_margins_wide
}

@PreviewLightDark
@Composable
private fun ReaderSettingsSheetPreview() {
    OmnibusTheme {
        ReaderSettingsSheetContent(
            settings = ReaderSettings(),
            accent = readerSheetAccent(ReaderBookState.Ready(title = "The Long Road Home"), OmnibusTheme.palette),
            onSettingsChanged = {},
            onDone = {},
        )
    }
}
