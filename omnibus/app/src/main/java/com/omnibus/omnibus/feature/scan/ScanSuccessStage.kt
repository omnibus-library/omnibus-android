package com.omnibus.omnibus.feature.scan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.components.FilledButton
import kotlin.random.Random

private const val CELEBRATION_MILLIS = 1_600
private const val CONFETTI_PIECES = 14
private const val HALO_LOW = 0.35f
private const val HALO_HIGH = 0.8f
private const val STILL_HALO = 0.6f

/** The checked-in success: the cover in a celebration, the place the copy went, the title, and what to do next. */
@Composable
internal fun ScanSuccessStage(
    stage: ScanStage.Success,
    actions: ScanActions,
    wide: Boolean,
    reducedMotion: Boolean,
) {
    val palette = OmnibusTheme.palette
    val book = (stage.cover as SuccessCover.Library).book
    val badge = stringResource(
        if (stage.kind == SuccessKind.OffWishlist) R.string.scan_success_off_wishlist else R.string.scan_success_checked_in,
    )
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs)) {
            ScanCloseButton(actions.onClose)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ScanCelebration(reducedMotion = reducedMotion, modifier = Modifier.width(160.dp)) {
                ScanLibraryCover(book.uuid, book.title, book.authors, book.coverUrl != null, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = badge,
                style = monoTextStyle(11, palette.accent),
                modifier = Modifier
                    .testTag(ScanTestTags.TITLE)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stage.title,
                style = displayTextStyle(26, palette.ink0),
                textAlign = TextAlign.Center,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (wide) Modifier.widthIn(max = 300.dp) else Modifier)
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilledButton(text = stringResource(R.string.scan_scan_another), onClick = actions.onScanAnother)
            FilledButton(
                text = stringResource(R.string.scan_view_book),
                onClick = { actions.onViewBook(stage.bookUuid) },
                prominent = false,
            )
        }
    }
}

/**
 * The cover with its celebration: a halo ring that loops, and a burst of confetti that falls once. Under reduced motion
 * the ring is static and there is no confetti. The drawing is hidden from TalkBack.
 */
@Composable
private fun ScanCelebration(reducedMotion: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val palette = OmnibusTheme.palette
    val transition = rememberInfiniteTransition(label = "scanHalo")
    val haloAlpha by transition.animateFloat(
        initialValue = HALO_LOW,
        targetValue = HALO_HIGH,
        animationSpec = infiniteRepeatable(tween(CELEBRATION_MILLIS), RepeatMode.Reverse),
        label = "scanHaloAlpha",
    )
    val confetti = remember { Animatable(0f) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) confetti.animateTo(1f, tween(CELEBRATION_MILLIS))
    }
    val colors = listOf(palette.accent, palette.ok, palette.warn, palette.ink2)
    Box(modifier) {
        Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
            Canvas(Modifier.fillMaxSize().testTag(ScanTestTags.CELEBRATION)) {
                drawHalo(alpha = if (reducedMotion) STILL_HALO else haloAlpha, color = palette.accent)
                if (!reducedMotion) drawConfetti(progress = confetti.value, colors = colors)
            }
        }
        content()
    }
}

private fun DrawScope.drawHalo(alpha: Float, color: Color) {
    val pad = 12.dp.toPx()
    val topLeft = Offset(-pad, -pad)
    val rect = Size(size.width + 2 * pad, size.height + 2 * pad)
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = topLeft,
        size = rect,
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 2.dp.toPx()),
    )
    drawRoundRect(
        color = color.copy(alpha = alpha * 0.25f),
        topLeft = topLeft,
        size = rect,
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 8.dp.toPx()),
    )
}

private fun DrawScope.drawConfetti(progress: Float, colors: List<Color>) {
    val pieceWidth = 6.dp.toPx()
    val pieceHeight = 10.dp.toPx()
    repeat(CONFETTI_PIECES) { index ->
        val random = Random(index)
        val x = random.nextFloat() * size.width
        val top = random.nextFloat() * size.height * 0.2f
        val fall = progress * size.height * (0.4f + random.nextFloat() * 0.6f)
        drawRect(
            color = colors[index % colors.size],
            topLeft = Offset(x, top + fall),
            size = Size(pieceWidth, pieceHeight),
        )
    }
}
