package com.omnibus.omnibus.feature.quotecard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.DisplayFontFamily
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.UiFontFamily
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.MonoFontFamily
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The Type section: the face, the size step, and the Bold and Italic toggles. */
@Composable
internal fun QuoteTypeSection(
    state: QuoteCardUIState,
    onTypefaceChosen: (QuoteTypeface) -> Unit,
    onSizeChosen: (QuoteSize) -> Unit,
    onBoldToggled: () -> Unit,
    onItalicToggled: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(stringResource(R.string.quote_card_type))
        SegmentedControl(
            options = QuoteTypeface.entries,
            selected = state.typeface,
            onSelected = onTypefaceChosen,
            modifier = Modifier.fillMaxWidth(),
        ) { face, ink ->
            val label = when (face) {
                QuoteTypeface.Serif -> R.string.quote_card_typeface_serif
                QuoteTypeface.Sans -> R.string.quote_card_typeface_sans
                QuoteTypeface.Mono -> R.string.quote_card_typeface_mono
            }
            val family = when (face) {
                QuoteTypeface.Serif -> DisplayFontFamily
                QuoteTypeface.Sans -> UiFontFamily
                QuoteTypeface.Mono -> MonoFontFamily
            }
            Text(stringResource(label), style = TextStyle(color = ink, fontFamily = family, fontSize = 17.sp, fontWeight = FontWeight.Medium))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SegmentedControl(
                options = QuoteSize.entries,
                selected = state.size,
                onSelected = onSizeChosen,
                modifier = Modifier.weight(1f),
            ) { size, ink ->
                val (label, description) = when (size) {
                    QuoteSize.S -> R.string.quote_card_size_small to R.string.quote_card_size_small_description
                    QuoteSize.M -> R.string.quote_card_size_medium to R.string.quote_card_size_medium_description
                    QuoteSize.L -> R.string.quote_card_size_large to R.string.quote_card_size_large_description
                }
                val spoken = stringResource(description)
                Text(
                    stringResource(label),
                    modifier = Modifier.semantics { contentDescription = spoken },
                    style = uiTextStyle(15, FontWeight.SemiBold, ink),
                )
            }
            TogglePill(stringResource(R.string.quote_card_bold), state.bold, onBoldToggled, QuoteCardTestTags.BOLD) { ink ->
                uiTextStyle(15, FontWeight.SemiBold, ink)
            }
            TogglePill(stringResource(R.string.quote_card_italic), state.italic, onItalicToggled, QuoteCardTestTags.ITALIC) { ink ->
                uiTextStyle(15, FontWeight.Medium, ink).copy(fontStyle = FontStyle.Italic)
            }
        }
    }
}

/** The Shape section: four pills, each with its name over its ratio. */
@Composable
internal fun QuoteShapeSection(shape: QuoteShape, onShapeChosen: (QuoteShape) -> Unit) {
    val palette = OmnibusTheme.palette
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(stringResource(R.string.quote_card_shape))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuoteShape.entries.forEach { option ->
                val selected = option == shape
                val ink = if (selected) palette.accentInk else palette.ink1
                val (name, ratio) = when (option) {
                    QuoteShape.Square -> R.string.quote_card_shape_square to R.string.quote_card_ratio_square
                    QuoteShape.Post -> R.string.quote_card_shape_post to R.string.quote_card_ratio_post
                    QuoteShape.Portrait -> R.string.quote_card_shape_portrait to R.string.quote_card_ratio_portrait
                    QuoteShape.Story -> R.string.quote_card_shape_story to R.string.quote_card_ratio_story
                }
                Column(
                    Modifier
                        .weight(1f)
                        .testTag(QuoteCardTestTags.shape(option))
                        .heightIn(min = 56.dp)
                        .clip(CircleShape)
                        .background(if (selected) palette.accent else palette.bg2)
                        .selectable(selected = selected, role = Role.RadioButton) { onShapeChosen(option) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(name), style = uiTextStyle(14, FontWeight.SemiBold, ink), maxLines = 1)
                    Text(stringResource(ratio), style = monoTextStyle(11, ink))
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, style = displayTextStyle(23, OmnibusTheme.palette.ink0), modifier = Modifier.semantics { heading() })
}

/** A capsule of mutually exclusive options. The chosen one is filled with the accent and [label] draws each in the ink it gets. */
@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T, Color) -> Unit,
) {
    val palette = OmnibusTheme.palette
    Row(modifier.clip(CircleShape).background(palette.bg2).selectableGroup()) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) palette.accent else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelected(option) },
                contentAlignment = Alignment.Center,
            ) {
                label(option, if (isSelected) palette.accentInk else palette.ink1)
            }
        }
    }
}

/** A pill that is on or off, filled with the accent when on. */
@Composable
private fun TogglePill(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String,
    style: (Color) -> TextStyle,
) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .testTag(tag)
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(if (checked) palette.accent else palette.bg2)
            .toggleable(value = checked, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style(if (checked) palette.accentInk else palette.ink1))
    }
}
