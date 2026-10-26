package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import kotlinx.serialization.Serializable

/**
 * The Library tab's push destination for one book. A stub until the detail ticket lands.
 *
 * @property uuid The book's stable identity, as [com.omnibus.omnibus.data.library.Book.uuid].
 */
@Serializable
data class BookDetailRoute(val uuid: String) : NavKey

/**
 * Placeholder detail screen. It has no back button: system back pops it, as for any pushed entry.
 *
 * @param uuid The book to show once the detail screen exists.
 */
@Composable
fun BookDetailStubScreen(uuid: String, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.book_detail_stub_title),
            style = displayTextStyle(27, palette.ink0),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.book_detail_stub_uuid, uuid),
            style = monoTextStyle(12, palette.ink3),
        )
    }
}

@PreviewLightDark
@Composable
private fun BookDetailStubScreenPreview() {
    OmnibusTheme {
        BookDetailStubScreen(uuid = "7f3c2a9e-preview")
    }
}
