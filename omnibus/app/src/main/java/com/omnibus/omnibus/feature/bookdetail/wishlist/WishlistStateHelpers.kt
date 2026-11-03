package com.omnibus.omnibus.feature.bookdetail.wishlist

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.wishlist.WishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.data.wishlist.di.WishlistEntryPoint
import com.omnibus.omnibus.feature.bookdetail.actionbar.ActionBarModel
import com.omnibus.omnibus.feature.bookdetail.actionbar.hasReadableCopy
import dagger.hilt.android.EntryPointAccessors

/** Overrides the wishlist repository for a composition. Instrumented tests provide a fake; production leaves it unset. */
internal val LocalWishlistRepository = staticCompositionLocalOf<WishlistRepository?> { null }

/**
 * The shared wishlist repository for the composition: the [LocalWishlistRepository] override when one is provided,
 * otherwise the app's, or null in previews and in hosts without the Hilt application.
 */
@Composable
internal fun rememberWishlistRepository(): WishlistRepository? {
    LocalWishlistRepository.current?.let { return it }
    val inspecting = LocalInspectionMode.current
    val appContext = LocalContext.current.applicationContext
    return remember(appContext, inspecting) { if (inspecting) null else wishlistRepositoryOrNull(appContext) }
}

private fun wishlistRepositoryOrNull(context: Context): WishlistRepository? = try {
    EntryPointAccessors.fromApplication(context.applicationContext, WishlistEntryPoint::class.java)
        .wishlistRepository()
} catch (_: IllegalStateException) {
    null
} catch (_: ClassCastException) {
    null
}

/**
 * The shared wishlist state for [book]. A book with a readable copy gets [WishlistState.Unknown] without touching the
 * repository, and so does a composition with no repository.
 */
@Composable
internal fun rememberWishlistState(book: Book): WishlistState {
    if (hasReadableCopy(book)) return WishlistState.Unknown
    val repository = rememberWishlistRepository() ?: return WishlistState.Unknown
    val flow = remember(repository, book.uuid) { repository.getWishlistFlow(book.uuid) }
    return flow.collectAsState(initial = WishlistState.Unknown).value
}

/** Whether [book] is wishlist-only. A book with a readable copy is false without any repository access. */
@Composable
internal fun rememberIsWishlistOnly(book: Book): Boolean {
    if (hasReadableCopy(book)) return false
    val state = rememberWishlistState(book)
    return isWishlistOnly(book, state)
}

/**
 * The action bar's wishlist variant for [book], or null unless it is wishlist-only. A book with a readable copy is null
 * without any repository access.
 */
@Composable
internal fun rememberWishlistActionModel(book: Book): ActionBarModel.Wishlist? {
    if (hasReadableCopy(book)) return null
    val state = rememberWishlistState(book)
    return remember(book, state) { wishlistActionModel(book, state) }
}
