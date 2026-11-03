package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.actionbar.hasReadableCopy
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The wishlist line above the title of a wishlist-only book. It renders nothing for a book with a readable copy, before
 * it touches the repository. It reads the shared state, so it needs no ViewModel.
 */
internal object WishlistStageSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val book = context.book
        if (hasReadableCopy(book)) return
        val repository = rememberWishlistRepository()
        val state = rememberWishlistState(book)
        val lastKey = remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(context.reloadKey) {
            val previous = lastKey.value
            lastKey.value = context.reloadKey
            if (previous != null && previous != context.reloadKey) repository?.refresh(context.book.uuid)
        }
        if (state is WishlistState.Present) WishlistLine(state.entry.source, modifier)
    }
}

/** The source phrase for the stage line, or null for a source this app does not know. */
@StringRes
internal fun wishlistSourcePhraseRes(source: WishlistSource?): Int? = when (source) {
    WishlistSource.Scan -> R.string.book_detail_wishlist_source_scan
    WishlistSource.Detail -> R.string.book_detail_wishlist_source_detail
    WishlistSource.Manual -> R.string.book_detail_wishlist_source_manual
    WishlistSource.Search -> R.string.book_detail_wishlist_source_search
    null -> null
}

/**
 * The centred line "On your wishlist · added from …". It sits 22 dp below the cover and, through its layout, reports
 * 14 dp less height, so the stage's fixed spacer leaves 10 dp above the title.
 */
@Composable
internal fun WishlistLine(source: WishlistSource?, modifier: Modifier = Modifier) {
    val phrase = wishlistSourcePhraseRes(source)
    val text = if (phrase == null) {
        stringResource(R.string.book_detail_wishlist_line_no_source)
    } else {
        stringResource(R.string.book_detail_wishlist_line, stringResource(phrase))
    }
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height - 14.dp.roundToPx()).coerceAtLeast(0)
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
            .padding(top = 22.dp),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().testTag(WishlistTestTags.LINE),
            style = uiTextStyle(14, FontWeight.Normal, OmnibusTheme.palette.ink1),
            textAlign = TextAlign.Center,
        )
    }
}
