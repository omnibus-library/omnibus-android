package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.R
import com.omnibus.omnibus.screenshot.Shot

/** The title, detail, Go and Dismiss of the device offer and both ebook offers. */
@PreviewTest
@PreviewLightDark
@Composable
fun ResumeOfferBanners() {
    Shot {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ResumeOfferBanner(
                title = stringResource(R.string.audiobook_offers_device_title),
                detail = stringResource(R.string.audiobook_offers_device_detail, "1:42:10"),
                onGo = {},
                onDismiss = {},
            )
            ResumeOfferBanner(
                title = stringResource(R.string.audiobook_offers_ebook_ahead_title),
                detail = stringResource(R.string.audiobook_offers_ebook_detail, "6:38"),
                onGo = {},
                onDismiss = {},
            )
            ResumeOfferBanner(
                title = stringResource(R.string.audiobook_offers_ebook_behind_title),
                detail = stringResource(R.string.audiobook_offers_ebook_detail, "6:38"),
                onGo = {},
                onDismiss = {},
            )
        }
    }
}
