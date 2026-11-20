package com.omnibus.omnibus.feature.bookdetail.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The opacity of a disabled row. */
private const val DisabledAlpha = 0.42f

/**
 * The More menu: a Material dropdown in the app's theme, anchored under the More disc, as wide as its longest row, with
 * each entry's rows in order.
 *
 * @param onDismiss Closes the menu. Passed to each entry as its `dismissMenu`.
 */
@Composable
internal fun BookMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    context: BookDetailSectionContext,
    entries: List<BookMoreMenuEntry>,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(BookMoreTestTags.MENU),
        offset = DpOffset(0.dp, 6.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = OmnibusTheme.palette.bg2,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        entries.forEach { entry -> entry.Rows(context, onDismiss) }
    }
}

/**
 * One row for a menu entry, shared by every entry: a 21 dp leading icon, 17 sp label and an optional 13 sp sub-line, in
 * the theme's ink. A disabled row is drawn at 42 % and ignores taps.
 */
@Composable
internal fun BookMoreMenuRow(
    label: String,
    @DrawableRes icon: Int,
    enabled: Boolean,
    subLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val ink = palette.ink0
    DropdownMenuItem(
        text = {
            Column {
                Text(label, style = uiTextStyle(17, FontWeight.Normal, ink))
                if (subLabel != null) Text(subLabel, style = uiTextStyle(13, FontWeight.Normal, palette.ink2))
            }
        },
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else DisabledAlpha),
        enabled = enabled,
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = ink,
            )
        },
        colors = MenuDefaults.itemColors(
            textColor = ink,
            leadingIconColor = ink,
            disabledTextColor = ink,
            disabledLeadingIconColor = ink,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    )
}
