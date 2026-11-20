package com.omnibus.omnibus.feature.library.addbooks

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** Upload, Scan and Wishlist, all enabled. */
@PreviewTest
@PreviewLightDark
@Composable
fun AddBooksSheetOptions() {
    Shot {
        AddBooksSheetContent(addBooksOptions(canUpload = true, isOffline = false), {}, {}, Modifier.background(OmnibusTheme.palette.bg0))
    }
}

/** Offline: Upload dimmed with its offline description. */
@PreviewTest
@PreviewLightDark
@Composable
fun AddBooksSheetOffline() {
    Shot {
        AddBooksSheetContent(addBooksOptions(canUpload = true, isOffline = true), {}, {}, Modifier.background(OmnibusTheme.palette.bg0))
    }
}
