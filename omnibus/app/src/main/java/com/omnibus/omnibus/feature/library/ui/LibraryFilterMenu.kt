package com.omnibus.omnibus.feature.library.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The category's menu and heading label. */
@StringRes
internal fun LibraryCategory.labelRes(): Int = when (this) {
    LibraryCategory.All -> R.string.library_category_all
    LibraryCategory.Ebooks -> R.string.library_category_ebooks
    LibraryCategory.Audiobooks -> R.string.library_category_audiobooks
}

/** The heading over the grid: "All books" for the default category, otherwise the category's own label. */
@StringRes
internal fun LibraryCategory.headingRes(): Int =
    if (this == LibraryCategory.All) R.string.library_all_books else labelRes()

/** The sort key's menu label. */
@StringRes
internal fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.Title -> R.string.library_sort_title
    LibrarySort.Author -> R.string.library_sort_author
    LibrarySort.Series -> R.string.library_sort_series
    LibrarySort.RecentlyInteracted -> R.string.library_sort_recently_interacted
    LibrarySort.LastUpdated -> R.string.library_sort_last_updated
    LibrarySort.NewestAdded -> R.string.library_sort_newest_added
}

@DrawableRes
private fun LibraryCategory.iconRes(): Int = when (this) {
    LibraryCategory.All -> R.drawable.ic_category_all
    LibraryCategory.Ebooks -> R.drawable.ic_category_ebooks
    LibraryCategory.Audiobooks -> R.drawable.ic_category_audiobooks
}

/**
 * The circular filter control beside the heading. Its fill and glyph animate between the neutral
 * default and the accent used while the grid is filtered. The border stays in both states.
 *
 * @param isFiltered Whether the grid is narrowed or reordered from the default view.
 * @param onClick Called when the button is tapped.
 */
@Composable
internal fun LibraryFilterButton(
    isFiltered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val label = stringResource(R.string.library_filter_sort)
    val fill by animateColorAsState(
        targetValue = if (isFiltered) palette.accent else palette.bg2,
        animationSpec = Motion.snap(),
        label = "filterFill",
    )
    val glyph by animateColorAsState(
        targetValue = if (isFiltered) palette.accentInk else palette.ink1,
        animationSpec = Motion.snap(),
        label = "filterGlyph",
    )

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(0.5.dp, palette.line2, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(LibraryTestTags.FILTER_BUTTON),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_filter_decrease),
            contentDescription = null,
            tint = glyph,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * The filter button and its dropdown menu. Menu-open state is local to this control; the menu
 * reports each choice up and closes itself first.
 *
 * @param category The category currently shown, marked in the Show group.
 * @param sort The sort currently applied, marked in the Sort group.
 * @param direction The direction currently applied, shown on the Direction row.
 * @param isFiltered Whether the grid is filtered, which drives the button's accent.
 */
@Composable
internal fun LibraryFilterControl(
    category: LibraryCategory,
    sort: LibrarySort,
    direction: SortDirection,
    isFiltered: Boolean,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSelectSort: (LibrarySort) -> Unit,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        LibraryFilterButton(isFiltered = isFiltered, onClick = { expanded = true })
        LibraryFilterMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            category = category,
            sort = sort,
            direction = direction,
            onSelectCategory = onSelectCategory,
            onSelectSort = onSelectSort,
            onToggleDirection = onToggleDirection,
        )
    }
}

/**
 * The menu's three groups in order: Show (single-select category), Sort (single-select key) and the
 * Direction row. Dividers separate the groups; there are no visible group headers.
 */
@Composable
private fun LibraryFilterMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    category: LibraryCategory,
    sort: LibrarySort,
    direction: SortDirection,
    onSelectCategory: (LibraryCategory) -> Unit,
    onSelectSort: (LibrarySort) -> Unit,
    onToggleDirection: () -> Unit,
) {
    val palette = OmnibusTheme.palette

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 250.dp),
        offset = DpOffset(0.dp, 4.dp),
        shape = RoundedCornerShape(Radius.lg),
        containerColor = palette.bg1,
        tonalElevation = 0.dp,
        border = BorderStroke(0.5.dp, palette.line2),
    ) {
        LibraryCategory.entries.forEach { option ->
            MenuRow(
                label = stringResource(option.labelRes()),
                tag = LibraryTestTags.menuCategory(option),
                selected = option == category,
                onClick = {
                    onDismiss()
                    onSelectCategory(option)
                },
                icon = option.iconRes(),
            )
        }
        MenuDivider()
        LibrarySort.entries.forEach { option ->
            MenuRow(
                label = stringResource(option.labelRes()),
                tag = LibraryTestTags.menuSort(option),
                selected = option == sort,
                onClick = {
                    onDismiss()
                    onSelectSort(option)
                },
            )
        }
        MenuDivider()
        MenuRow(
            label = stringResource(
                if (direction == SortDirection.Asc) {
                    R.string.library_direction_ascending
                } else {
                    R.string.library_direction_descending
                },
            ),
            tag = LibraryTestTags.MENU_DIRECTION,
            selected = null,
            onClick = {
                onDismiss()
                onToggleDirection()
            },
            icon = if (direction == SortDirection.Asc) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down,
            iconTag = LibraryTestTags.directionIcon(direction),
        )
    }
}

/**
 * One row of the filter menu. [selected] adds the check mark and selected semantics; null means the
 * row is an action with neither.
 */
@Composable
private fun MenuRow(
    label: String,
    tag: String,
    selected: Boolean?,
    onClick: () -> Unit,
    @DrawableRes icon: Int? = null,
    iconTag: String? = null,
) {
    val palette = OmnibusTheme.palette
    val selectedModifier = selected?.let { isSelected -> Modifier.semantics { this.selected = isSelected } }
        ?: Modifier

    DropdownMenuItem(
        text = { Text(label, style = uiTextStyle(15, FontWeight.Normal, palette.ink0)) },
        onClick = onClick,
        modifier = Modifier.testTag(tag).then(selectedModifier),
        leadingIcon = if (icon != null) {
            {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = palette.ink1,
                    modifier = Modifier.size(20.dp).then(iconTag?.let { Modifier.testTag(it) } ?: Modifier),
                )
            }
        } else {
            null
        },
        trailingIcon = if (selected == true) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = palette.ink0,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 0.5.dp,
        color = OmnibusTheme.palette.line2,
    )
}

@PreviewLightDark
@Composable
private fun LibraryFilterButtonPreview() {
    OmnibusTheme {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LibraryFilterButton(isFiltered = false, onClick = {})
            Spacer(Modifier.width(Spacing.sm))
            LibraryFilterButton(isFiltered = true, onClick = {})
        }
    }
}
