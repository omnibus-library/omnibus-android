package com.omnibus.omnibus.feature.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** How strongly a request button asks to be pressed. */
internal enum class RequestEmphasis {
    /** Filled accent, for the one action to take. */
    Primary,

    /** A raised ground, for the rest. */
    Secondary,

    /** Bare text, for quiet ones. */
    Ghost,
}

/** The height of a request button and of the fixed-width ones in a list. */
internal val RequestButtonHeight = 40.dp

/**
 * The request screens' capsule button. A disabled one stays drawn, dimmed, so a press that would be ignored looks
 * ignored.
 *
 * @param width A fixed width, so buttons that swap labels in a list stay one size with the label centred.
 * @param fullWidth Stretches the button to its parent's width.
 * @param contentDescription What a screen reader says in place of [text], such as which book a row's Request is for.
 */
@Composable
internal fun RequestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: RequestEmphasis = RequestEmphasis.Secondary,
    enabled: Boolean = true,
    width: Dp? = null,
    fullWidth: Boolean = false,
    contentDescription: String? = null,
) {
    val palette = OmnibusTheme.palette
    val background = when (emphasis) {
        RequestEmphasis.Primary -> palette.accent
        RequestEmphasis.Secondary -> palette.bg2
        RequestEmphasis.Ghost -> Color.Transparent
    }
    val content = when (emphasis) {
        RequestEmphasis.Primary -> palette.accentInk
        RequestEmphasis.Secondary -> palette.ink0
        RequestEmphasis.Ghost -> palette.ink1
    }
    val size = when {
        width != null -> Modifier.width(width).height(RequestButtonHeight)
        fullWidth -> Modifier.fillMaxWidth().heightIn(min = RequestButtonHeight)
        else -> Modifier.heightIn(min = RequestButtonHeight)
    }
    Box(
        modifier = modifier
            .then(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (width != null) 6.dp else if (emphasis == RequestEmphasis.Ghost) 4.dp else 16.dp)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = uiTextStyle(14, FontWeight.SemiBold, content),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val DISABLED_ALPHA = 0.45f
