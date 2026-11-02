package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** Test tags for the More button and its menu. */
internal object BookMoreTestTags {
    const val MORE_BUTTON = "book-detail-more-button"
    const val MENU = "book-detail-more-menu"
    const val MENU_RULE = "book-detail-more-menu-rule"
    const val GROUP = "book-detail-more-menu-group"
}

/**
 * Whether the More menu has been opened on this page. Entries with a Hilt ViewModel check it before creating the
 * ViewModel in `Overlay`, so a page that never opens the menu composes no More entry state.
 */
internal val LocalMoreMenuActivated = compositionLocalOf { false }

/** The More (⋯) disc in the Book Detail top bar, which opens the More menu built from [moreMenuEntries]. */
internal object BookMoreButton : BookDetailSection {
    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        BookMoreButtonContent(context, moreMenuEntries, modifier)
    }
}

/**
 * The More disc and its menu. Each entry's [BookMoreMenuEntry.Overlay] is composed beside the disc, outside the
 * menu's anchor, so an overlay's size never moves the menu.
 *
 * @param entries The menu's entries, in order.
 */
@Composable
internal fun BookMoreButtonContent(
    context: BookDetailSectionContext,
    entries: List<BookMoreMenuEntry>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var activated by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalMoreMenuActivated provides activated) {
        Box(modifier) {
            Box {
                MoreDisc(onClick = {
                    // Set before the menu opens, so the entries' first ViewModel call happens in the same frame.
                    activated = true
                    expanded = true
                })
                BookMoreMenu(expanded, onDismiss = { expanded = false }, context = context, entries = entries)
            }
            entries.forEach { it.Overlay(context) }
        }
    }
}

/** The round More button, styled like the back disc. */
@Composable
private fun MoreDisc(onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .testTag(BookMoreTestTags.MORE_BUTTON)
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.bg0.copy(alpha = 0.42f))
            .border(1.dp, palette.ink0.copy(alpha = 0.10f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_more_dots),
            contentDescription = stringResource(R.string.book_detail_more),
            tint = if (isLightGround(palette.bg0)) palette.ink0 else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
