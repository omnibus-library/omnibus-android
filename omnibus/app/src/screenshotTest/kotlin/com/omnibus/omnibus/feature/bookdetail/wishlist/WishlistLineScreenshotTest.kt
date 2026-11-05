package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.screenshot.Shot

/** The stage line for each source, then for an unknown one, which has no phrase. */
@PreviewTest
@PreviewLightDark
@Composable
fun WishlistLines() {
    Shot {
        // The line reports 14 dp less height than it draws, so each one needs room below it.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WishlistLine(WishlistSource.Scan)
            WishlistLine(WishlistSource.Detail)
            WishlistLine(WishlistSource.Manual)
            WishlistLine(WishlistSource.Search)
            WishlistLine(null)
            Spacer(Modifier.height(24.dp))
        }
    }
}
