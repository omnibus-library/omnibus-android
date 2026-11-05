package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.status.rememberStatusRim
import com.omnibus.omnibus.feature.bookdetail.wishlist.rememberWishlistActionModel
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The bottom action bar of the Book Detail page. It is self-gating: a book with no readable copy renders before any
 * ViewModel is created, so such a book needs no Hilt. That is [ActionBarModel.NoCopy], or the wishlist variant when the
 * book is on the wishlist. A book with both an ebook and an audiobook also gets its round buttons, from [roundActions].
 */
internal object BookActionBarSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val book = context.book
        val colors = remember(book) { actionBarColors(book) }
        if (!hasReadableCopy(book)) {
            val model = rememberWishlistActionModel(book) ?: ActionBarModel.NoCopy(physical = book.hasPhysical)
            val checkIn = CheckInPillColors(wash = context.accent.tintSoft, rim = rememberStatusRim(book))
            BookActionBar(model, colors, context.onNavigate, modifier, checkInColors = checkIn)
            return
        }
        val viewModel = hiltViewModel<ActionBarViewModel, ActionBarViewModel.Factory>(
            key = "action-bar-${book.uuid}",
            creationCallback = { it.create(book.uuid) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        val model = remember(book, state) { actionBarModel(book, state.status, state.progress) }
        val savedFileId = state.progress?.audioFileId
        val actions = remember(book, model, savedFileId, state.listenAvailable) {
            (model as? ActionBarModel.Primary)?.let { roundActions(book, it, savedFileId, state.listenAvailable) }.orEmpty()
        }
        val light = isLightGround(OmnibusTheme.palette.bg0)
        val listenColors = remember(book.accent, book.displayTitle, light) {
            listenButtonColors(book.coverIdentity().tone, light)
        }
        BookActionBarContent(
            bookUuid = book.uuid,
            model = model,
            roundActions = actions,
            colors = colors,
            listenColors = listenColors,
            chooserAccent = context.accent.tint,
            onNavigate = context.onNavigate,
            onLoadAudio = viewModel::onImmersiveRead,
            modifier = modifier,
        )
    }
}
