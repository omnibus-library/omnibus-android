package com.omnibus.omnibus.feature.library.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The "group by series" pill beside the filter button: a stack glyph and a compact switch in one control.
 *
 * It takes the accent when on, like the filter button when filtered, and is one switch node for TalkBack.
 *
 * @param checked Whether the grid is grouped by series.
 * @param onToggle Called when the pill is tapped.
 */
@Composable
internal fun GroupBySeriesSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val label = stringResource(R.string.library_group_by_series)
    val fill by animateColorAsState(
        targetValue = if (checked) palette.accent else palette.bg2,
        animationSpec = Motion.snap(),
        label = "groupSwitchFill",
    )
    val glyph by animateColorAsState(
        targetValue = if (checked) palette.accentInk else palette.ink1,
        animationSpec = Motion.snap(),
        label = "groupSwitchGlyph",
    )
    val track by animateColorAsState(
        targetValue = if (checked) palette.accentInk else palette.ink3,
        animationSpec = Motion.snap(),
        label = "groupSwitchTrack",
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) palette.accent else Color.White,
        animationSpec = Motion.snap(),
        label = "groupSwitchThumb",
    )

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(fill)
            .border(0.5.dp, palette.line2, CircleShape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = label }
            .testTag(LibraryTestTags.GROUP_SWITCH)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_series_stack),
            contentDescription = null,
            tint = glyph,
            modifier = Modifier.size(14.dp),
        )
        Box(
            Modifier.size(width = 26.dp, height = 16.dp).clip(CircleShape).background(track).padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(thumb))
        }
    }
}
