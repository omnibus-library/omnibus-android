package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * A row's footer actions: the ⋯ button and its menu (Make a quote card, and Share the passage as text) for a highlight
 * that has a passage, nothing for one that has none. Each callback gets that row's passage.
 */
@Composable
internal fun HighlightRowActions(
    row: HighlightRowModel,
    onQuoteCard: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = row.text ?: return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .size(48.dp)
                .clickable(role = Role.Button) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_book_detail_more_dots),
                contentDescription = stringResource(R.string.book_detail_highlights_actions),
                tint = OmnibusTheme.palette.ink1,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(276.dp),
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = Color(0xEB282420),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            BookMoreMenuRow(
                label = stringResource(R.string.book_detail_highlights_quote_card),
                icon = R.drawable.ic_book_detail_more_quote,
                enabled = true,
                subLabel = null,
                onClick = { expanded = false; onQuoteCard(text) },
            )
            BookMoreMenuRow(
                label = stringResource(R.string.book_detail_highlights_share),
                icon = R.drawable.ic_book_detail_more_export,
                enabled = true,
                subLabel = null,
                onClick = { expanded = false; onShare(text) },
            )
        }
    }
}
