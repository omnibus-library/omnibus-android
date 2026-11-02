package com.omnibus.omnibus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the offline pill, so UI tests can find it. */
internal object OfflinePillTestTags {
    const val PILL = "offline-pill"
}

/**
 * A capsule that says the device is offline. It slides in from the top with a fade while [visible]
 * and leaves the same way.
 *
 * @param visible Whether the pill is shown. Hidden, it takes no space once the exit animation ends.
 */
@Composable
fun OfflinePill(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(Motion.glide(IntOffset.VisibilityThreshold)) { -it } + fadeIn(Motion.glide()),
        exit = slideOutVertically(Motion.glide(IntOffset.VisibilityThreshold)) { -it } + fadeOut(Motion.glide()),
    ) {
        Row(
            modifier = Modifier
                .testTag(OfflinePillTestTags.PILL)
                .clip(CircleShape)
                .background(palette.bg2)
                .border(0.5.dp, palette.line2, CircleShape)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_wifi_off),
                contentDescription = null,
                tint = palette.ink1,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.library_offline),
                style = uiTextStyle(12, FontWeight.Medium, palette.ink1),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OfflinePillPreview() {
    OmnibusTheme {
        OfflinePill(visible = true)
    }
}
