package com.omnibus.omnibus.feature.bookdetail.more

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The sub-line colour of a row, such as the offline note. */
private val SubLabelInk = Color(0xFFC8C4BE)

/** The opacity of a disabled row. */
private const val DisabledAlpha = 0.42f

/** The thick rule's height between entries. */
private val GroupRuleHeight = 8.dp

/** The thick rule's colour. */
private val GroupRuleColor = Color.Black.copy(alpha = 0.28f)

/**
 * The indices of the groups that get a rule above them: every non-empty group after the first non-empty one, except
 * a group that [joinsPrevious] marks as joining the group above it. An entry that emitted nothing (height 0) gets no
 * rule, so rules never stack or lead the menu.
 */
internal fun menuRuleSlots(groupHeights: List<Int>, joinsPrevious: List<Boolean> = emptyList()): List<Int> {
    val first = groupHeights.indexOfFirst { it > 0 }
    if (first < 0) return emptyList()
    return groupHeights.indices.filter {
        it > first && groupHeights[it] > 0 && !joinsPrevious.getOrElse(it) { false }
    }
}

/**
 * The More menu: a 276 dp dropdown anchored under the More disc, with each entry's rows and the thick rule between
 * entries that emitted rows.
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
        modifier = Modifier.width(276.dp).testTag(BookMoreTestTags.MENU),
        offset = DpOffset(0.dp, 6.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xEB282420),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        MenuGroups(entries) { entry -> entry.Rows(context, onDismiss) }
    }
}

/**
 * One row for a menu entry, shared by every entry: 17 sp label, an optional 13 sp sub-line, a 21 dp trailing icon.
 * A disabled row is drawn at 42 % and ignores taps.
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
    val ink = OmnibusTheme.palette.ink0
    DropdownMenuItem(
        text = {
            Column {
                Text(label, style = uiTextStyle(17, FontWeight.Normal, ink))
                if (subLabel != null) Text(subLabel, style = uiTextStyle(13, FontWeight.Normal, SubLabelInk))
            }
        },
        onClick = onClick,
        modifier = modifier.alpha(if (enabled) 1f else DisabledAlpha),
        enabled = enabled,
        trailingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = ink,
            )
        },
        colors = MenuDefaults.itemColors(
            textColor = ink,
            trailingIconColor = ink,
            disabledTextColor = ink,
            disabledTrailingIconColor = ink,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Stacks each entry's group, with one [GroupRuleHeight] rule above each non-empty group after the first. A
 * multi-content [Layout] rather than `SubcomposeLayout`, because the menu's column measures intrinsics first.
 */
@Composable
private fun MenuGroups(entries: List<BookMoreMenuEntry>, rows: @Composable (BookMoreMenuEntry) -> Unit) {
    val joins = entries.map { it.joinsPreviousGroup }
    Layout(
        contents = listOf(
            {
                entries.forEach { entry ->
                    Column(Modifier.fillMaxWidth().testTag(BookMoreTestTags.GROUP)) { rows(entry) }
                }
            },
            {
                repeat((entries.size - 1).coerceAtLeast(0)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(GroupRuleHeight)
                            .background(GroupRuleColor)
                            .testTag(BookMoreTestTags.MENU_RULE),
                    )
                }
            },
        ),
        measurePolicy = object : MultiContentMeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<List<Measurable>>,
                constraints: Constraints,
            ): MeasureResult {
                val (groups, rules) = measurables
                val bounded = constraints.hasBoundedWidth
                val placedGroups = groups.map {
                    it.measure(if (bounded) Constraints.fixedWidth(constraints.maxWidth) else Constraints())
                }
                val width = if (bounded) constraints.maxWidth else placedGroups.maxOfOrNull { it.width } ?: 0
                val slots = menuRuleSlots(placedGroups.map { it.height }, joins)
                val ruleConstraints = Constraints.fixedWidth(width)
                val placedRules = rules.take(slots.size).map { it.measure(ruleConstraints) }
                val height = placedGroups.sumOf { it.height } + placedRules.sumOf { it.height }
                return layout(width, height) {
                    var y = 0
                    var rule = 0
                    placedGroups.forEachIndexed { index, group ->
                        if (index in slots) {
                            placedRules[rule].place(0, y)
                            y += placedRules[rule].height
                            rule++
                        }
                        group.place(0, y)
                        y += group.height
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<List<IntrinsicMeasurable>>,
                height: Int,
            ): Int = measurables[0].maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<List<IntrinsicMeasurable>>,
                height: Int,
            ): Int = measurables[0].maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<List<IntrinsicMeasurable>>,
                width: Int,
            ): Int {
                val heights = measurables[0].map { it.minIntrinsicHeight(width) }
                return heights.sum() + menuRuleSlots(heights, joins).size * GroupRuleHeight.roundToPx()
            }

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<List<IntrinsicMeasurable>>,
                width: Int,
            ): Int = minIntrinsicHeight(measurables, width)
        },
    )
}
