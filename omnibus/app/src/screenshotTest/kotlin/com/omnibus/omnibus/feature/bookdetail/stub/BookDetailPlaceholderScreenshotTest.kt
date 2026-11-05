package com.omnibus.omnibus.feature.bookdetail.stub

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun BookDetailPlaceholder() {
    Shot {
        BookDetailPlaceholderScreen(title = "Frank Herbert")
    }
}
