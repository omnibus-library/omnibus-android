package com.omnibus.omnibus.feature.libraries.switcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibraryHost
import com.omnibus.omnibus.feature.libraries.LibraryMonogram
import com.omnibus.omnibus.feature.libraries.accountLine
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.monogramOf
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for a library row. */
internal object LibraryRowTestTags {
    fun row(libraryId: String) = "library-row-$libraryId"
}

/**
 * One library in a list: its monogram, its name and "‹username› · ‹host›", with a check when it is the open library.
 * The row is one TalkBack element that reads the name, username and host, and "current" as its state.
 *
 * @param library The library to show.
 * @param isCurrent Whether this is the library open in the app. It gets the accent tile and the check.
 * @param onClick Called when the row is tapped.
 * @param showCheck Whether the current library gets the check. Manage libraries shows a chevron instead.
 * @param trailing Shown at the row's end, before the check, for tags such as "Signed out".
 */
@Composable
fun LibraryRow(
    library: Library,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCheck: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    val name = library.displayName()
    val host = LibraryHost.of(library.address)
    val label = library.username?.let { stringResource(R.string.libraries_switcher_row_description, name, it, host) }
        ?: stringResource(R.string.libraries_switcher_row_description_no_username, name, host)
    val current = stringResource(R.string.libraries_switcher_row_current)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                if (isCurrent) stateDescription = current
                role = Role.Button
                onClick { onClick(); true }
                testTag = LibraryRowTestTags.row(library.id)
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tileColor = if (isCurrent) palette.accent.copy(alpha = CURRENT_TILE_ALPHA) else palette.bg2
        Box(
            Modifier.size(TILE).clip(RoundedCornerShape(8.dp)).background(tileColor),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides if (isCurrent) palette.accentText else palette.ink1) {
                LibraryMonogram(monogramOf(name), TILE)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                library.accountLine(),
                style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
        if (isCurrent && showCheck) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = palette.accentText,
                modifier = Modifier.padding(end = Spacing.xs).size(20.dp),
            )
        }
    }
}

private val TILE: Dp = 40.dp

/** The same tint as the account card's tile. */
private const val CURRENT_TILE_ALPHA = 0.16f
