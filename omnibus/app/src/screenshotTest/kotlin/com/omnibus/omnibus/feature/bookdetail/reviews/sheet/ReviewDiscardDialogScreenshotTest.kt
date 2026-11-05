package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun ReviewDiscardDialogShown() {
    Shot {
        ReviewDiscardDialog(onDiscard = {}, onKeepEditing = {})
    }
}
