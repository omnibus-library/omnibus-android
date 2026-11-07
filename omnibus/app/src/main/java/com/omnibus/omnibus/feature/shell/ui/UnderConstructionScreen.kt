package com.omnibus.omnibus.feature.shell.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * A tab whose real screen hasn't been built yet.
 *
 * @property title The joke's setup, shown large.
 * @property message The rest of the joke.
 */
internal enum class UnderConstructionPage(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
) {
    Search(R.string.under_construction_search_title, R.string.under_construction_search_message),
    Stats(R.string.under_construction_stats_title, R.string.under_construction_stats_message),
    You(R.string.under_construction_you_title, R.string.under_construction_you_message),
}

/**
 * Stands in for a tab's root until its own ticket supplies its content: a librarian stocking a bookcase over a line of
 * copy. The illustration loops unless the system asks for no animation. It scrolls so the copy stays reachable on a
 * short window, such as a phone held sideways.
 */
@Composable
internal fun UnderConstructionScreen(page: UnderConstructionPage, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg0)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LibrarianIllustration(
            page = page,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(SCENE_WIDTH / SCENE_HEIGHT),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .size(26.dp, 1.5.dp)
                .background(palette.accent),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.under_construction_kicker),
            style = monoTextStyle(9.5.sp, palette.ink3).copy(letterSpacing = 1.6.sp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(page.title),
            style = displayTextStyle(29, palette.ink0),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(page.message),
            style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink2),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

@Composable
private fun LibrarianIllustration(page: UnderConstructionPage, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val colors = sceneColors(palette, isLightGround(palette.bg0))
    val reserved = stringResource(R.string.under_construction_reserved)
    val measurer = rememberTextMeasurer()
    // Measured at a density of 1 so its size is in scene units, and scales with the drawing rather than the font scale.
    val reservedLabel = remember(reserved, colors.ink1) {
        measurer.measure(
            text = reserved,
            style = monoTextStyle(5.4.sp, colors.ink1),
            density = Density(1f),
        )
    }
    val phase by rememberScenePhase()

    // The phase is read in the draw lambda, so the loop only redraws this canvas.
    Canvas(modifier) {
        drawLibrarianScene(page, phase, colors, reservedLabel)
    }
}

/** The illustration's position in its loop, held at [SCENE_REST_PHASE] where nothing should move. */
@Composable
private fun rememberScenePhase(): State<Float> {
    if (LocalInspectionMode.current || rememberReducedMotion()) {
        return remember { mutableFloatStateOf(SCENE_REST_PHASE) }
    }
    return rememberInfiniteTransition(label = "librarian").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SCENE_LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "librarian phase",
    )
}

private const val SCENE_LOOP_MILLIS = 3200

@Composable
@PreviewLightDark
private fun UnderConstructionScreenPreview() {
    OmnibusTheme {
        UnderConstructionScreen(UnderConstructionPage.Search)
    }
}
