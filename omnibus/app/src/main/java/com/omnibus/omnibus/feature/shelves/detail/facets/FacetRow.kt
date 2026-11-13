package com.omnibus.omnibus.feature.shelves.detail.facets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.feature.shelves.detail.ui.ShelfDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The facet row: a Clear chip while any facet is set, then one chip per field. A set facet comes first, filled, and
 * reads its value. Tapping a chip calls [onOpenFacet] with its field.
 */
@Composable
internal fun FacetRow(
    clauses: List<FilterClause>,
    shelfNames: Map<Long, String>,
    onOpenFacet: (FilterField) -> Unit,
    onClearFacets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier.testTag(ShelfDetailTestTags.FACET_ROW),
    ) {
        if (showsClear(clauses)) {
            item(key = "clear") {
                ClearChip(onClick = onClearFacets)
            }
        }
        items(facetChips(clauses), key = { it.field.wire }) { chip ->
            val label = facetLabelOf(chip) { shelfNames[it] }
            val fieldName = stringResource(fieldNameRes(chip.field))
            val value = label.first?.let { value ->
                buildValue(value, label.excluded, label.more)
            } ?: stringResource(R.string.shelf_detail_facet_unavailable).takeIf { chip.clause != null }
            val spoken = if (value == null) {
                stringResource(R.string.shelf_detail_facet_a11y, fieldName)
            } else {
                "${stringResource(R.string.shelf_detail_facet_a11y, fieldName)}, $value"
            }
            val active = chip.clause != null
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) palette.bg2 else palette.bg0, CircleShape)
                    .border(0.5.dp, if (active) palette.accent.copy(alpha = 0.45f) else palette.line, CircleShape)
                    .clickable(role = Role.Button) { onOpenFacet(chip.field) }
                    .testTag(ShelfDetailTestTags.facet(chip.field))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        role = Role.Button
                    },
            ) {
                Text(
                    text = if (value == null) fieldName else "$fieldName: $value",
                    style = uiTextStyle(13.sp, FontWeight.Medium, palette.ink0),
                )
                if (!active) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_down),
                        contentDescription = null,
                        tint = palette.ink3,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/** The Clear chip: an accent outline with a cross, removing every facet. */
@Composable
private fun ClearChip(onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, palette.accent.copy(alpha = 0.45f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(ShelfDetailTestTags.CLEAR_FACETS)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clearAndSetSemantics {
                contentDescription = "Clear all filters"
                role = Role.Button
            },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = stringResource(R.string.shelf_detail_facet_clear),
            style = uiTextStyle(13.sp, FontWeight.Medium, palette.accent),
        )
    }
}

/** The value as the chip shows it: "not " before an exclude, and " +N" for each further value. */
@Composable
private fun buildValue(first: String, excluded: Boolean, more: Int): String {
    val prefix = if (excluded) stringResource(R.string.shelf_detail_facet_not) else ""
    val suffix = if (more > 0) stringResource(R.string.shelf_detail_facet_more, more) else ""
    return prefix + first + suffix
}

/** The field's name, as the chip and the sheet title show it. */
@StringRes
internal fun fieldNameRes(field: FilterField): Int = when (field) {
    FilterField.Shelf -> R.string.shelf_detail_facet_shelf
    FilterField.Tag -> R.string.shelf_detail_facet_tag
    FilterField.Genre -> R.string.shelf_detail_facet_genre
    FilterField.Author -> R.string.shelf_detail_facet_author
    FilterField.Series -> R.string.shelf_detail_facet_series
    FilterField.Format -> R.string.shelf_detail_facet_format
}

/** The field's plural noun, as an error names what could not load. */
@StringRes
internal fun fieldPluralRes(field: FilterField): Int = when (field) {
    FilterField.Shelf -> R.string.shelf_detail_facet_plural_shelf
    FilterField.Tag -> R.string.shelf_detail_facet_plural_tag
    FilterField.Genre -> R.string.shelf_detail_facet_plural_genre
    FilterField.Author -> R.string.shelf_detail_facet_plural_author
    FilterField.Series -> R.string.shelf_detail_facet_plural_series
    FilterField.Format -> R.string.shelf_detail_facet_plural_format
}
