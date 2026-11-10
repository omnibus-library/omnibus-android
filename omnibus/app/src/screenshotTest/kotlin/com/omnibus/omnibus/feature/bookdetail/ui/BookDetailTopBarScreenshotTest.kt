package com.omnibus.omnibus.feature.bookdetail.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.R
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The top bar unfilled over a bright cover-like ground (its scrim darkens it), then filled over the page ground. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 240)
@Preview(name = "Dark", widthDp = 360, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookDetailTopBarStates() {
    Shot {
        Column(Modifier.fillMaxSize()) {
            BarOver(ground = Color(0xFFF3E9C6), filled = false)
            BarOver(ground = OmnibusTheme.palette.bg0, filled = true)
        }
    }
}

/** The bar over a [ground] band of its own, so the preview shows what the bar draws over that ground. */
@Composable
private fun BarOver(ground: Color, filled: Boolean) {
    Box(Modifier.fillMaxWidth().height(120.dp).background(ground)) {
        BookDetailTopBar(
            title = "The Long Way Home",
            filled = filled,
            onBack = {},
            reducedMotion = true,
            actions = {
                IconButton(onClick = {}) {
                    Icon(painter = painterResource(R.drawable.ic_book_detail_more_dots), contentDescription = null)
                }
            },
        )
    }
}
