package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The destructive row in the More menu. It draws nothing when the row is [DeleteRow.Hidden]. Offline, the row is dimmed
 * and shows the needs-connection sub-line.
 *
 * `BookMoreMenuRow` has no colour parameter, so this row repeats its metrics: 17 sp label, 13 sp sub-line, a 21 dp icon,
 * 16 x 8 dp padding, and 42 % alpha when disabled.
 */
@Composable
internal fun DeleteMenuRow(row: DeleteRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (row) {
        DeleteRow.Hidden -> return
        is DeleteRow.Enabled -> row.label
        is DeleteRow.DisabledOffline -> row.label
    }
    val enabled = row is DeleteRow.Enabled
    val ink = if (enabled) DeleteColors.RowInk else OmnibusTheme.palette.ink0
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .testTag(DeleteTestTags.MENU_ROW)
            .destructiveClick(enabled, onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(rowLabel(label), style = uiTextStyle(17, FontWeight.Normal, ink))
            if (!enabled) {
                Text(
                    stringResource(R.string.book_detail_more_needs_connection),
                    style = uiTextStyle(13, FontWeight.Normal, DeleteColors.Muted),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_more_delete),
            contentDescription = null,
            modifier = Modifier.size(21.dp).testTag(DeleteTestTags.MENU_ICON),
            tint = ink,
        )
    }
}

/** The opacity of a disabled row, the same as `BookMoreMenuRow`'s. */
private const val DISABLED_ALPHA = 0.42f
