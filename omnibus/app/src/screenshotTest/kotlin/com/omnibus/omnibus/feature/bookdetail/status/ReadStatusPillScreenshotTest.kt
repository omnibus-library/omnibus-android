package com.omnibus.omnibus.feature.bookdetail.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme

// The menu opens only on a tap, so it is not shot here; ReadStatusPillTest covers its rows.

/** The pill for an unknown status, which says Reading status rather than Unread, then each known status. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReadStatusPillLabels() {
    Shot {
        val tone = Oklch(0.62, 0.10, 200.0)
        val light = isLightGround(OmnibusTheme.palette.bg0)
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, ReadStatus.Unread, ReadStatus.Reading, ReadStatus.Finished).forEach { status ->
                ReadStatusPill(
                    uiState = ReadStatusUIState(status = status),
                    accent = bookAccent(tone, light),
                    rim = statusRim(tone, light),
                    onSelect = {},
                    reducedMotion = true,
                )
            }
        }
    }
}
