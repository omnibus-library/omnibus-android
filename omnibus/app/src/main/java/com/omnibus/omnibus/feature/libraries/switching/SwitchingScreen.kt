package com.omnibus.omnibus.feature.libraries.switching

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.libraries.LibraryMonogram
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.monogramOf
import com.omnibus.omnibus.ui.components.FilledButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the switching screen. */
internal object SwitchingTestTags {
    const val NAME = "switching_name"
    const val STATUS = "switching_status"
    const val FAILURE_TITLE = "switching_failure_title"
    const val STAY = "switching_stay"
    const val SWITCH_ANYWAY = "switching_switch_anyway"
}

/**
 * The switching screen for [route] (#369): a plain screen for now, with the library's name and monogram and the
 * stage's status. It closes when the reader stays, and hands over to the shell once the app has opened on the target.
 */
@Composable
fun SwitchingScreen(
    route: SwitchingRoute,
    onLeave: () -> Unit,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SwitchingViewModel = hiltViewModel<SwitchingViewModel, SwitchingViewModel.Factory>(
        creationCallback = { it.create(route) },
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.left) {
        if (uiState.left) onLeave()
    }
    LaunchedEffect(uiState.opened) {
        if (uiState.opened) onOpened()
    }
    SwitchingScreenContent(
        uiState = uiState,
        onStay = viewModel::onStay,
        onSwitchAnyway = viewModel::onSwitchAnyway,
        modifier = modifier,
    )
}

/**
 * The switching screen's content, without its view model. Back does nothing except in the sync-failed stage, where it
 * stays on the current library, as "Stay" does. The screen never leaves on Back in any other stage.
 */
@Composable
internal fun SwitchingScreenContent(
    uiState: SwitchingUIState,
    onStay: () -> Unit,
    onSwitchAnyway: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {
        if (uiState.stage == SwitchingStage.SyncFailed) onStay()
    }

    val palette = OmnibusTheme.palette
    val stage = uiState.stage
    // The current library is shown while the switch syncs and fails; the target once the switch lifts.
    val shown = if (stage == SwitchingStage.Lifting || stage == SwitchingStage.Opening) uiState.target else uiState.current
    val name = shown?.displayName().orEmpty()
    val currentName = uiState.current?.displayName().orEmpty()
    val targetName = uiState.target?.displayName().orEmpty()
    val announcement = stringResource(R.string.libraries_switching_announce, targetName)

    Box(
        modifier
            .fillMaxSize()
            .background(palette.bg0)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { paneTitle = announcement },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // The monogram tile stands in for the illuminated initial until the bookcase lands; decorative only.
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.bg2)
                    .border(1.dp, palette.accent, RoundedCornerShape(12.dp))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides palette.ink0) {
                    LibraryMonogram(monogramOf(name), 40.dp)
                }
            }
            Text(
                text = name,
                style = displayTextStyle(36.sp, palette.ink0),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(SwitchingTestTags.NAME),
            )

            when (stage) {
                SwitchingStage.SyncFailed -> SyncFailedContent(
                    currentName = currentName,
                    onStay = onStay,
                    onSwitchAnyway = onSwitchAnyway,
                )
                else -> StatusContent(stage = stage, currentName = currentName, targetName = targetName)
            }
        }
    }
}

/** The status line, with a progress bar while the switch is syncing or opening. Empty while lifting, so nothing jumps. */
@Composable
private fun StatusContent(stage: SwitchingStage?, currentName: String, targetName: String) {
    val palette = OmnibusTheme.palette
    val status = when (stage) {
        SwitchingStage.Syncing -> stringResource(R.string.libraries_switching_syncing, currentName)
        SwitchingStage.Opening -> stringResource(R.string.libraries_switching_opening, targetName)
        else -> ""
    }
    Text(
        text = status,
        style = uiTextStyle(16, FontWeight.Normal, palette.ink2),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .testTag(SwitchingTestTags.STATUS)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (stage == SwitchingStage.Syncing || stage == SwitchingStage.Opening) {
        LinearProgressIndicator(
            modifier = Modifier.width(140.dp).clearAndSetSemantics {},
            color = palette.accent,
            trackColor = palette.line2,
        )
    }
}

/** The sync-failed state, in place: the title takes focus, and the two choices are at least 48 dp tall. */
@Composable
private fun SyncFailedContent(currentName: String, onStay: () -> Unit, onSwitchAnyway: () -> Unit) {
    val palette = OmnibusTheme.palette
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        titleFocus.requestFocus()
    }
    Text(
        text = stringResource(R.string.libraries_switching_failed_title, currentName),
        style = displayTextStyle(26.sp, palette.ink0),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .testTag(SwitchingTestTags.FAILURE_TITLE)
            .semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
            .focusRequester(titleFocus)
            .focusable(),
    )
    Text(
        text = stringResource(R.string.libraries_switching_failed_body, currentName),
        style = uiTextStyle(16, FontWeight.Normal, palette.ink2),
        textAlign = TextAlign.Center,
    )
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FilledButton(
            text = stringResource(R.string.libraries_switching_stay, currentName),
            onClick = onStay,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(SwitchingTestTags.STAY),
        )
        FilledButton(
            text = stringResource(R.string.libraries_switching_switch_anyway),
            onClick = onSwitchAnyway,
            prominent = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(SwitchingTestTags.SWITCH_ANYWAY),
        )
    }
}
