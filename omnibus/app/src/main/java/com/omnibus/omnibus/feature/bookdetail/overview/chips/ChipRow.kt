package com.omnibus.omnibus.feature.bookdetail.overview.chips

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The width of a row's label, so Genres and Tags chips start at the same edge. */
internal val ChipRowLabelWidth = 64.dp

/** How tall a chip is drawn. Its touch target is [ChipTouchSize] tall. */
internal val ChipHeight = 34.dp

/** The least size of a chip's touch target. */
internal val ChipTouchSize = 44.dp

/**
 * One filing row: [label], then chips that scroll sideways. [content] adds the row's items, so a caller can put a
 * trailing item (such as an add chip) after the values.
 */
@Composable
internal fun ChipRow(label: String, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = uiTextStyle(15, FontWeight.SemiBold, OmnibusTheme.palette.ink0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ChipRowLabelWidth),
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A filing chip: a capsule outline drawn [ChipHeight] tall inside a touch target of at least [ChipTouchSize]. */
@Composable
internal fun StageChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ink = OmnibusTheme.palette.ink0
    Box(
        modifier
            .heightIn(min = ChipTouchSize)
            .widthIn(min = ChipTouchSize)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = ChipHeight)
                .border(1.dp, ink.copy(alpha = 0.24f), CircleShape)
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = uiTextStyle(15, FontWeight.Medium, ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
