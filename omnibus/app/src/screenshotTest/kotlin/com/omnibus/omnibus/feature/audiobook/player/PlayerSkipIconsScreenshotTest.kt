package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.R
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * Back 15 and forward 30, drawn large over a crosshair through the icon's centre, where each ring is centred. The
 * digits sit in the middle of the crosshair; digits drawn off-centre in the ring show as a lopsided cross.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun PlayerSkipIcons() {
    Shot {
        Row {
            SkipIcon(R.drawable.ic_audiobook_player_back_15)
            SkipIcon(R.drawable.ic_audiobook_player_forward_30)
        }
    }
}

@Composable
private fun SkipIcon(iconRes: Int) {
    val palette = OmnibusTheme.palette
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier
            .size(200.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(palette.ink3, Offset(center.x, 0f), Offset(center.x, size.height), stroke)
                drawLine(palette.ink3, Offset(0f, center.y), Offset(size.width, center.y), stroke)
            },
        tint = palette.ink0,
    )
}
