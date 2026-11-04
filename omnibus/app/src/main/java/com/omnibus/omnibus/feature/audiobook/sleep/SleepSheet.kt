package com.omnibus.omnibus.feature.audiobook.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat
import com.omnibus.omnibus.ui.theme.MonoFontFamily
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The player's sleep sheet, bound to [SleepViewModel]. Back, the scrim and a drag down all call [onDismiss]. */
@Composable
fun SleepSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SleepSheetModal(uiState, viewModel::onSelect, onDismiss, modifier)
}

/**
 * The sleep sheet's window: a modal bottom sheet around [SleepSheetContent]. It never calls [onDismiss] on a pick, so the
 * countdown shows at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepSheetModal(
    uiState: SleepUIState,
    onSelect: (SleepChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bg0,
        contentColor = palette.ink0,
    ) {
        SleepSheetContent(uiState, onSelect, Modifier.fillMaxWidth())
    }
}

/**
 * The sheet's body without its window: a title with the countdown while a timer runs, the two-column grid of presets and
 * the full-width end-of-chapter cell under it. It sizes to its content.
 */
@Composable
internal fun SleepSheetContent(
    uiState: SleepUIState,
    onSelect: (SleepChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timed = uiState.choices.filter { it !is SleepChoice.EndOfChapter }
    val endOfChapter = uiState.choices.firstOrNull { it is SleepChoice.EndOfChapter }

    Column(
        modifier.testTag(SleepTestTags.SHEET).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val palette = OmnibusTheme.palette
            Text(
                text = stringResource(R.string.audiobook_sleep_title),
                style = uiTextStyle(20, FontWeight.SemiBold, palette.ink0),
                modifier = Modifier.weight(1f).testTag(SleepTestTags.TITLE),
            )
            uiState.remainingSeconds?.let { SleepCountdown(it) }
        }

        Column(
            Modifier.fillMaxWidth().testTag(SleepTestTags.OPTIONS).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            timed.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { choice ->
                        SleepCell(
                            label = choice.label(),
                            selected = choice == uiState.selected,
                            tag = SleepTestTags.of(choice),
                            onClick = { onSelect(choice) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            endOfChapter?.let { choice ->
                SleepCell(
                    label = choice.label(),
                    selected = choice == uiState.selected,
                    tag = SleepTestTags.of(choice),
                    onClick = { onSelect(choice) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The header countdown, with the clock time in the mono face. Plain text: never a live region (R14). */
@Composable
private fun SleepCountdown(remaining: Double) {
    val palette = OmnibusTheme.palette
    val clock = PlaybackTimeFormat.clock(remaining, alwaysHours = true)
    val text = stringResource(R.string.audiobook_sleep_remaining, clock)
    Text(
        text = buildAnnotatedString {
            append(text)
            val start = text.indexOf(clock)
            if (start >= 0) {
                addStyle(SpanStyle(fontFamily = MonoFontFamily, color = palette.accent), start, start + clock.length)
            }
        },
        style = uiTextStyle(13, FontWeight.Normal, palette.ink3),
        modifier = Modifier.testTag(SleepTestTags.HEADER_COUNTDOWN),
    )
}

/**
 * One option cell: at least 44 dp high, on `bg2`. The selected cell gets an accent fill and border. `selectable` gives
 * TalkBack the label and the selected state, so no custom state string is set.
 */
@Composable
private fun SleepCell(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.md)
    Box(
        modifier
            .testTag(tag)
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) palette.accent.copy(alpha = 0.14f) else palette.bg2)
            .then(if (selected) Modifier.border(1.dp, palette.accent, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = uiTextStyle(15, FontWeight.Medium, if (selected) palette.accent else palette.ink1),
        )
    }
}

/** The cell's spoken and visible label. */
@Composable
private fun SleepChoice.label(): String = when (this) {
    SleepChoice.Off -> stringResource(R.string.audiobook_sleep_off)
    is SleepChoice.Timed ->
        if (minutes < 60) {
            stringResource(R.string.audiobook_sleep_minutes, minutes)
        } else {
            pluralStringResource(R.plurals.audiobook_sleep_hours, minutes / 60, minutes / 60)
        }
    is SleepChoice.EndOfChapter -> stringResource(R.string.audiobook_sleep_end_of_chapter, chapterNumber)
}
