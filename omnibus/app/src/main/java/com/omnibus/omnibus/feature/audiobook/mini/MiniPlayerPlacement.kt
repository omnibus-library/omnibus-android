package com.omnibus.omnibus.feature.audiobook.mini

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute

/** Whether the shell hides the mini player for the selected tab's top entry [top] (P-MINI-1): only on Book Detail. */
internal fun shellHidesMiniPlayer(top: NavKey?): Boolean = top is BookDetailRoute
