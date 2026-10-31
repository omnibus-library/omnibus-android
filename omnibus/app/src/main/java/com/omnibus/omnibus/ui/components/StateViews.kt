package com.omnibus.omnibus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * A full-screen spinner with a label, shown while the first page of a screen loads.
 *
 * The label is a parameter so each screen can say what it is loading.
 */
@Composable
fun LoadingStateView(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = OmnibusTheme.palette.bg0,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                color = palette.ink2,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
            )
        }
    }
}

/**
 * A full-screen failure with a retry action.
 *
 * Sized as a screen state rather than a banner, so the error takes the whole content area.
 * [onRetry] runs on each tap; the caller decides whether that starts a reload.
 */
@Composable
fun ErrorStateView(
    title: String,
    message: String,
    actionLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = OmnibusTheme.palette.bg0,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_alert_triangle),
                contentDescription = null,
                tint = palette.bad,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .size(26.dp, 1.5.dp)
                    .background(palette.bad),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = displayTextStyle(27, palette.ink0),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink2),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Spacer(Modifier.height(20.dp))
            StateActionButton(label = actionLabel, onClick = onRetry)
        }
    }
}

/**
 * A full-screen "nothing here yet" state with a ghost-plate illustration.
 *
 * Plates are drawn behind the centre plate and are not clipped, so they may extend past the
 * illustration's box. [action] is optional and is only shown when non-null.
 *
 * @param kicker Small mono label above the title, or null to omit it.
 * @param glyph Drawable shown on the centre plate.
 * @param action Optional button below the message.
 */
@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    @DrawableRes glyph: Int = R.drawable.ic_tab_library,
    action: StateAction? = null,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg0),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GhostPlate(glyph = glyph)
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .size(26.dp, 1.5.dp)
                    .background(palette.accent),
            )
            Spacer(Modifier.height(14.dp))
            if (kicker != null) {
                Text(
                    text = kicker,
                    style = monoTextStyle(9.5.sp, palette.ink3).copy(letterSpacing = 1.6.sp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = title,
                style = displayTextStyle(29, palette.ink0),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink2),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                StateActionButton(label = action.label, onClick = action.onClick)
            }
        }
    }
}

/**
 * An optional action for [EmptyStateView], passed as one value so its label and callback stay
 * together.
 */
data class StateAction(val label: String, val onClick: () -> Unit)

/**
 * The capsule button used by the state views: accent text on a translucent accent fill.
 *
 * Exposed so screens can add the same control beside other content.
 */
@Composable
fun StateActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = 0.14f))
            .border(0.5.dp, palette.accent.copy(alpha = 0.45f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = uiTextStyle(14, FontWeight.Medium, palette.accent),
        )
    }
}

/**
 * The stacked-book illustration for [EmptyStateView]: two outline plates fanned out behind a
 * dashed centre plate that holds [glyph].
 */
@Composable
private fun GhostPlate(@DrawableRes glyph: Int) {
    val palette = OmnibusTheme.palette
    val plateShape = RoundedCornerShape(Radius.sm)
    Box(Modifier.size(74.dp, 111.dp)) {
        // Fanned plates first so the centre plate sits on top of them.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = -37.dp.toPx()
                    rotationZ = -11f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .border(0.5.dp, palette.line2, plateShape),
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = 37.dp.toPx()
                    rotationZ = 11f
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .border(0.5.dp, palette.line2, plateShape),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(lerp(palette.bg0, palette.bg1, 0.55f), plateShape)
                .drawBehind {
                    drawRoundRect(
                        color = palette.line,
                        cornerRadius = CornerRadius(Radius.sm.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                            ),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                tint = palette.ink3,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun LoadingStateViewPreview() {
    OmnibusTheme {
        LoadingStateView(label = "Loading your library")
    }
}

@PreviewLightDark
@Composable
private fun ErrorStateViewPreview() {
    OmnibusTheme {
        ErrorStateView(
            title = "Couldn't load this",
            message = "Could not connect to the server.",
            actionLabel = "Try again",
            onRetry = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun EmptyStateViewPreview() {
    OmnibusTheme {
        EmptyStateView(
            kicker = "YOUR LIBRARY",
            title = "No books yet",
            message = "Point the server at a library folder, or add a book from the You tab.",
        )
    }
}
