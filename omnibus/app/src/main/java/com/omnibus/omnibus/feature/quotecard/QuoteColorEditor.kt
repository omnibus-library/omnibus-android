package com.omnibus.omnibus.feature.quotecard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale

private const val TRACK_STOPS = 12
private val ThumbSize = 24.dp

/**
 * The inline editor for a custom colour: for a gradient, the From and To wells with a direction button, then three
 * sliders on the active colour. Changes go out as opaque ARGB through [onColorChanged].
 */
@Composable
internal fun QuoteColorEditor(
    background: QuoteBackground,
    activeStop: GradientStop,
    onColorChanged: (Int) -> Unit,
    onStopFocused: (GradientStop) -> Unit,
    onDirectionRotated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val argb = when (background) {
        is QuoteBackground.CustomSolid -> background.argb
        is QuoteBackground.CustomGradient -> if (activeStop == GradientStop.From) background.fromArgb else background.toArgb
        else -> return
    }
    Column(modifier.fillMaxWidth().testTag(QuoteCardTestTags.EDITOR), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (background is QuoteBackground.CustomGradient) {
            StopRow(background, activeStop, onStopFocused, onDirectionRotated)
        }
        // The sliders keep their own exact values: reading them back from 8-bit colour would make a thumb jitter as it drags.
        var picker by remember(background is QuoteBackground.CustomGradient, activeStop) { mutableStateOf(pickerOf(argb)) }
        fun changed(next: PickerValue) {
            picker = next
            onColorChanged(pickerArgb(next.hue, next.chroma, next.lightness))
        }
        PickerSlider(
            label = stringResource(R.string.quote_card_picker_hue),
            tag = "hue",
            value = picker.hue,
            range = 0.0..360.0,
            track = { v -> pickerArgb(v, picker.chroma, picker.lightness) },
            onValueChange = { changed(picker.copy(hue = it)) },
        )
        PickerSlider(
            label = stringResource(R.string.quote_card_picker_saturation),
            tag = "saturation",
            value = picker.chroma,
            range = 0.0..PICKER_MAX_CHROMA,
            track = { v -> pickerArgb(picker.hue, v, picker.lightness) },
            onValueChange = { changed(picker.copy(chroma = it)) },
        )
        PickerSlider(
            label = stringResource(R.string.quote_card_picker_lightness),
            tag = "lightness",
            value = picker.lightness,
            range = PICKER_MIN_LIGHTNESS..PICKER_MAX_LIGHTNESS,
            track = { v -> pickerArgb(picker.hue, picker.chroma, v) },
            onValueChange = { changed(picker.copy(lightness = it)) },
        )
    }
}

@Composable
private fun StopRow(
    gradient: QuoteBackground.CustomGradient,
    activeStop: GradientStop,
    onStopFocused: (GradientStop) -> Unit,
    onDirectionRotated: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Well(GradientStop.From, gradient.fromArgb, activeStop == GradientStop.From, onStopFocused, Modifier.weight(1f))
            Well(GradientStop.To, gradient.toArgb, activeStop == GradientStop.To, onStopFocused, Modifier.weight(1f))
        }
        val description = stringResource(R.string.quote_card_direction, gradient.angle)
        Box(
            Modifier
                .testTag(QuoteCardTestTags.DIRECTION)
                .size(48.dp)
                .clip(CircleShape)
                .background(palette.bg2)
                .clickable(role = Role.Button, onClick = onDirectionRotated)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_up),
                contentDescription = null,
                tint = palette.ink0,
                modifier = Modifier.size(22.dp).rotate(gradient.angle.toFloat()),
            )
        }
    }
}

/** One end of the gradient: its colour and `#RRGGBB`. Choosing it points the sliders at it. */
@Composable
private fun Well(stop: GradientStop, argb: Int, active: Boolean, onFocus: (GradientStop) -> Unit, modifier: Modifier) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(14.dp)
    val name = stringResource(if (stop == GradientStop.From) R.string.quote_card_stop_from else R.string.quote_card_stop_to)
    Row(
        modifier
            .testTag(QuoteCardTestTags.well(stop))
            .height(48.dp)
            .clip(shape)
            .background(palette.bg2)
            .border(if (active) 2.dp else 0.5.dp, if (active) palette.accent else palette.line, shape)
            .selectable(selected = active, role = Role.RadioButton) { onFocus(stop) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(argb)).border(1.dp, palette.ink0.copy(alpha = 0.14f), CircleShape))
        Column {
            Text(name, style = uiTextStyle(12, FontWeight.SemiBold, palette.ink2))
            Text(String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF), style = monoTextStyle(12, palette.ink0))
        }
    }
}

/** The value at pixel [x] of a track [width] wide whose thumb is [thumb] wide, so the thumb's centre follows the finger. */
private fun ClosedFloatingPointRange<Double>.valueAt(x: Float, width: Int, thumb: Float): Double =
    start + (endInclusive - start) * ((x - thumb / 2) / (width - thumb)).coerceIn(0f, 1f)

/**
 * A slider on a track painted with the colours it would pick: tap or drag to set it, and assistive technology can set it
 * through its progress semantics.
 */
@Composable
private fun PickerSlider(
    label: String,
    tag: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    track: (Double) -> Int,
    onValueChange: (Double) -> Unit,
) {
    val palette = OmnibusTheme.palette
    val update by rememberUpdatedState(onValueChange)
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0.0, 1.0).toFloat()
    val colors = (0..TRACK_STOPS).map { Color(track(range.start + (range.endInclusive - range.start) * it / TRACK_STOPS)) }
    val thumb = with(LocalDensity.current) { ThumbSize.toPx() }
    BoxWithConstraints(
        Modifier
            .testTag(QuoteCardTestTags.slider(tag))
            .fillMaxWidth()
            .height(36.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.start.toFloat()..range.endInclusive.toFloat())
                setProgress { target -> update(target.toDouble().coerceIn(range)); true }
            }
            .pointerInput(range) { detectTapGestures { update(range.valueAt(it.x, size.width, thumb)) } }
            .pointerInput(range) {
                detectHorizontalDragGestures(onDragStart = { update(range.valueAt(it.x, size.width, thumb)) }) { change, _ ->
                    change.consume()
                    update(range.valueAt(change.position.x, size.width, thumb))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .padding(horizontal = ThumbSize / 2)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(colors))
                .border(0.5.dp, palette.line, CircleShape),
        )
        Box(
            Modifier
                .offset(x = (maxWidth - ThumbSize) * fraction)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(Color(track(value)))
                .border(2.dp, palette.ink0, CircleShape),
        )
    }
}
