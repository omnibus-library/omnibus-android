package com.omnibus.omnibus.feature.libraries.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the account card. */
internal object AccountCardTestTags {
    const val CARD = "account-card"
}

/**
 * The account card on the You root (C6): the active library's monogram, its name and "‹username› · ‹host›", with a
 * "Switch" link. The whole card is one button for TalkBack, read as "‹name›, signed in as ‹username› on ‹host›.
 * Switch library".
 *
 * @param library The active library to show.
 * @param onClick Called when the card is tapped. It opens the switcher sheet.
 */
@Composable
fun AccountCard(library: Library, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val palette = OmnibusTheme.palette
    val name = library.displayName()
    val host = LibraryHost.of(library.address)
    val account = library.username?.let { stringResource(R.string.libraries_card_description, name, it, host) }
        ?: stringResource(R.string.libraries_card_description_no_username, name, host)
    val label = stringResource(R.string.libraries_switcher_card_description, account)
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.bg1)
            .border(1.dp, palette.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            // One TalkBack element: the link is part of the card's button, not a target of its own.
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick { onClick(); true }
                testTag = AccountCardTestTags.CARD
            }
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(TILE).clip(RoundedCornerShape(8.dp)).background(palette.accent.copy(alpha = TILE_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides palette.accentText) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.libraries_switcher_card_link),
                style = uiTextStyle(15, FontWeight.Medium, palette.accentText),
                maxLines = 1,
            )
            Icon(
                painterResource(R.drawable.ic_audiobook_player_chevron_down),
                contentDescription = null,
                tint = palette.accentText,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private val TILE: Dp = 40.dp

/** The same tint as the Requests pill. */
private const val TILE_ALPHA = 0.16f
