package com.omnibus.omnibus.feature.audiobook.speed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.audiobook.playback.MAX_RATE
import com.omnibus.omnibus.data.audiobook.playback.MIN_RATE
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale

/** The opacity of a step button that cannot move (P-SPD-4, the render's 38%). */
private const val DISABLED_ALPHA = 0.38f

/**
 * The speed sheet opened from the player's Speed cell (P-SPD-1, C11). Its state comes from [SpeedViewModel] over the
 * player's controller. Swipe down, a tap on the scrim and system back all arrive as [onDismiss]; the ViewModel saves a
 * drag the dismissal cut short, on the same path and again when the sheet leaves composition (I-SPD-2).
 *
 * @param onDismiss Called when the sheet is dismissed by any means, so the host clears its overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) {
        onDispose { viewModel.onSheetClosed() }
    }
    val palette = OmnibusTheme.palette
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.onSheetClosed()
            onDismiss()
        },
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bg0,
        contentColor = palette.ink0,
    ) {
        SpeedSheetContent(
            uiState = uiState,
            onPresetChosen = viewModel::onPresetChosen,
            onSliderMoved = viewModel::onSliderMoved,
            onSliderReleased = viewModel::onSliderReleased,
            onSlower = viewModel::onSlower,
            onFaster = viewModel::onFaster,
        )
    }
}

/**
 * The speed sheet's content, without its state or sheet: the header, the preset grid, the fine-tune slider and the step
 * buttons (R2-R8). Instrumented tests drive this with a [SpeedUIState].
 *
 * Numbers follow the composition's locale, so "1,25×" in German. The slider's live moves and its release arrive as
 * separate callbacks (R6).
 */
@Composable
internal fun SpeedSheetContent(
    uiState: SpeedUIState,
    onPresetChosen: (Double) -> Unit,
    onSliderMoved: (Float) -> Unit,
    onSliderReleased: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val rateLabel = currentRateLabel(uiState.rate, locale)
    val sliderDescription = stringResource(R.string.audiobook_speed_slider_description)

    Column(
        modifier
            .fillMaxWidth()
            .testTag(SpeedSheetTags.BODY)
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
    ) {
        // The header: the title as a heading, and the current rate on the same row (R2).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.audiobook_speed_title),
                style = displayTextStyle(24, palette.ink0, FontWeight.Medium),
                modifier = Modifier
                    .weight(1f)
                    .testTag(SpeedSheetTags.TITLE)
                    .semantics { heading() },
            )
            Text(
                text = rateLabel,
                style = monoTextStyle(15, palette.accent),
                modifier = Modifier.testTag(SpeedSheetTags.HEADER_RATE),
            )
        }

        // The preset grid: two rows of four, the selected preset filled (R3, R4, R8).
        Column(
            Modifier
                .padding(top = Spacing.lg)
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SPEED_PRESETS.chunked(PRESET_COLUMNS).forEachIndexed { row, presets ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    presets.forEachIndexed { column, preset ->
                        PresetCell(
                            preset = preset,
                            selected = uiState.selectedPreset == preset,
                            locale = locale,
                            tag = SpeedSheetTags.preset(row * PRESET_COLUMNS + column),
                            onClick = { onPresetChosen(preset) },
                        )
                    }
                }
            }
        }

        // The fine-tune caption row: the label, and the range's two ends (R5).
        Row(
            Modifier
                .padding(top = Spacing.lg)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.audiobook_speed_fine_tune),
                style = uiTextStyle(11, FontWeight.Normal, palette.ink3),
                modifier = Modifier
                    .weight(1f)
                    .testTag(SpeedSheetTags.FINE_TUNE),
            )
            Text(
                text = stringResource(
                    R.string.audiobook_speed_range,
                    presetLabel(MIN_RATE, locale),
                    presetLabel(MAX_RATE, locale),
                ),
                style = uiTextStyle(11, FontWeight.Normal, palette.ink3),
                modifier = Modifier.testTag(SpeedSheetTags.RANGE),
            )
        }

        // The slider (R5, R6, R8). Its ticks are hidden; its semantics name the slider and speak the rate.
        Slider(
            value = uiState.rate.toFloat(),
            onValueChange = onSliderMoved,
            valueRange = SLIDER_RANGE,
            steps = SLIDER_STEPS,
            onValueChangeFinished = onSliderReleased,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.bg2,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            // The outermost semantics win over the slider's own percentage state description.
            modifier = Modifier
                .padding(top = Spacing.xs)
                .fillMaxWidth()
                .testTag(SpeedSheetTags.SLIDER)
                .semantics {
                    contentDescription = sliderDescription
                    stateDescription = rateLabel
                },
        )

        // The step row: slower, the current rate over its step caption, faster (R7, R8).
        Row(
            Modifier
                .padding(top = Spacing.sm)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StepButton(
                glyph = "−",
                description = stringResource(R.string.audiobook_speed_slower),
                enabled = uiState.canSlower,
                onClick = onSlower,
                tag = SpeedSheetTags.SLOWER,
            )
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = rateLabel,
                    style = monoTextStyle(15, palette.ink0),
                    modifier = Modifier.testTag(SpeedSheetTags.STEP_RATE),
                )
                Text(
                    text = stringResource(R.string.audiobook_speed_step_caption, stepAmountLabel(locale)),
                    style = uiTextStyle(11, FontWeight.Normal, palette.ink3),
                    modifier = Modifier.testTag(SpeedSheetTags.STEP_CAPTION),
                )
            }
            StepButton(
                glyph = "+",
                description = stringResource(R.string.audiobook_speed_faster),
                enabled = uiState.canFaster,
                onClick = onFaster,
                tag = SpeedSheetTags.FASTER,
            )
        }
    }
}

/**
 * One preset cell: a radio button in the grid's group, filled with the accent when it is the current rate (R3, R8).
 * Its text is merged into the cell's semantics, so TalkBack reads "1.5×, radio button, selected".
 */
@Composable
private fun RowScope.PresetCell(
    preset: Double,
    selected: Boolean,
    locale: Locale,
    tag: String,
    onClick: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) palette.accent else palette.bg2)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = presetLabel(preset, locale),
            style = monoTextStyle(14, if (selected) palette.accentInk else palette.ink1),
        )
    }
}

/**
 * One of the "−" and "+" buttons: a 64 dp wide, at least 48 dp tall target that dims and stops responding at a bound (R7,
 * P-SPD-4). The glyph is cleared from semantics, so TalkBack reads [description] (R8, TC-89).
 */
@Composable
private fun StepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .width(64.dp)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(RoundedCornerShape(Radius.md))
            .background(palette.bg2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = monoTextStyle(19, palette.ink0),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
