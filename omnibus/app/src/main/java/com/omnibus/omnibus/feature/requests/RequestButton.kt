package com.omnibus.omnibus.feature.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
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

/** The width a Request or Open button keeps in every list, so it doesn't change size with its label. */
internal val RequestButtonWidth = 100.dp

/**
 * The request screens' capsule button. A disabled one stays drawn, dimmed, so a press that would be ignored looks
 * ignored.
 *
 * @param width The least width, so buttons that swap labels in a list stay one size with the label centred. A larger font
 * grows the button rather than clipping its label.
 * @param busy Whether the press is being handled. The button says so to a screen reader, whatever its description is.
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
    busy: Boolean = false,
    fullWidth: Boolean = false,
    contentDescription: String? = null,
) {
    val palette = OmnibusTheme.palette
    val background = when (emphasis) {
        RequestEmphasis.Primary -> palette.accent
        RequestEmphasis.Secondary -> palette.ink0.copy(alpha = SECONDARY_FILL_ALPHA)
        RequestEmphasis.Ghost -> Color.Transparent
    }
    val content = when (emphasis) {
        RequestEmphasis.Primary -> palette.accentInk
        RequestEmphasis.Secondary -> palette.ink0
        RequestEmphasis.Ghost -> palette.ink1
    }
    val size = when {
        width != null -> Modifier.widthIn(min = width).heightIn(min = RequestButtonHeight)
        fullWidth -> Modifier.fillMaxWidth().heightIn(min = RequestButtonHeight)
        else -> Modifier.heightIn(min = RequestButtonHeight)
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(size)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (width != null) 6.dp else if (emphasis == RequestEmphasis.Ghost) Spacing.xs else 16.dp)
            .then(
                // A busy button says so only when a description replaces its label; otherwise the label already does.
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        if (busy) stateDescription = text
                    }
                } else {
                    Modifier
                },
            ),
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

/** The secondary button's fill, a wash of the ink, so it reads on any ground a card puts it on. */
private const val SECONDARY_FILL_ALPHA = 0.08f
