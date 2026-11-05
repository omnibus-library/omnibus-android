package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The title, detail, Go and dismiss over each page theme. The banner follows the reading theme, so one shot covers all. */
@PreviewTest
@Preview
@Composable
fun ReaderSyncOfferBannerThemes() {
    Column {
        ReaderTheme.entries.forEach { theme ->
            OmnibusTheme(darkTheme = !theme.isLight) {
                Box(Modifier.background(Color(theme.groundArgb)).padding(12.dp)) {
                    ReaderSyncOfferBanner(
                        title = stringResource(R.string.reader_sync_offer_title),
                        detail = stringResource(R.string.reader_sync_offer_detail),
                        goLabel = stringResource(R.string.reader_sync_offer_go),
                        dismissLabel = stringResource(R.string.reader_sync_offer_dismiss),
                        colors = readerChromeColors(theme),
                        actionColor = readerSyncOfferActionColor(theme),
                        onAccept = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }
}
