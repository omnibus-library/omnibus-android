package com.omnibus.omnibus.feature.library.addbooks

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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

/** The dialog a wide window shows in place of the bottom sheet, on the Library's background. */
@PreviewTest
@Preview(name = "Light", widthDp = 840, heightDp = 560)
@Preview(name = "Dark", widthDp = 840, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddBooksDialogWide() {
    Shot {
        Box(Modifier.fillMaxSize().background(OmnibusTheme.palette.bg1), contentAlignment = Alignment.Center) {
            AddBooksDialogCard(addBooksOptions(canUpload = true, isOffline = false), {}, {})
        }
    }
}
