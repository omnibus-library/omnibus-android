package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The book's cover, eased down to 94% while paused (P-SCR-5). Preparing and buffering cover it with a scrim and a
 * spinner. The caller sizes it through [modifier]; its 2:3 shape then follows the height it is given.
 */
@Composable
internal fun PlayerCover(
    identity: CoverIdentity,
    isPlaying: Boolean,
    isPreparing: Boolean,
    isBuffering: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = if (reducedMotion) snap() else Motion.glide(),
        label = "coverScale",
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // After graphicsLayer, so the node's bounds include the scale.
            .testTag(PlayerTestTags.COVER)
            .coverShadow(10.dp),
    ) {
        // BookCover clears its own semantics, which is why the tag sits on this wrapper.
        BookCover(
            identity = identity,
            modifier = Modifier.fillMaxSize(),
            size = CoverSize.Lg,
            cornerRadius = 10.dp,
            keepsAspect = false,
        )
        if (isPreparing) {
            CoverScrim(
                label = stringResource(R.string.audiobook_player_preparing),
                tag = PlayerTestTags.PREPARING,
                scrimAlpha = 0.6f,
                shape = shape,
            )
        } else if (isBuffering) {
            CoverScrim(
                label = stringResource(R.string.audiobook_player_buffering),
                tag = PlayerTestTags.BUFFERING,
                scrimAlpha = 0.4f,
                shape = shape,
            )
        }
    }
}

/** A `bg0` scrim over the cover with a spinner and a label (R15). */
@Composable
private fun BoxScope.CoverScrim(label: String, tag: String, scrimAlpha: Float, shape: Shape) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .background(palette.bg0.copy(alpha = scrimAlpha))
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = palette.ink2,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = label,
                style = uiTextStyle(13, FontWeight.Medium, palette.ink1),
            )
        }
    }
}
