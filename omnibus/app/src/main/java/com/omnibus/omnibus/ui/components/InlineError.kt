package com.omnibus.omnibus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * A warning icon and message in the palette's error color that slides in from above when [message]
 * becomes non-null and back out when it returns to null.
 */
@Composable
fun InlineError(message: String?, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn(Motion.settle()) + slideInVertically(Motion.settle(IntOffset(1, 1))) { -it },
        exit = fadeOut(Motion.settle()) + slideOutVertically(Motion.settle(IntOffset(1, 1))) { -it },
    ) {
        // Keeps the last message on screen while the exit animation runs.
        var lastMessage by remember { mutableStateOf("") }
        if (message != null) lastMessage = message

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = WarningTriangle,
                contentDescription = null,
                tint = palette.bad,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = lastMessage,
                style = uiTextStyle(13, FontWeight.Normal, palette.bad),
            )
        }
    }
}

private val WarningTriangle = ImageVector.Builder(
    name = "WarningTriangle",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(1f, 21f)
        horizontalLineToRelative(22f)
        lineTo(12f, 2f)
        close()
        moveTo(13f, 18f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(2f)
        close()
        moveTo(13f, 14f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(2f)
        close()
    }
}.build()
