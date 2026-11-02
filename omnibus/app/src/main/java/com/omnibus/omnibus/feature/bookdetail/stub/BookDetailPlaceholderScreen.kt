package com.omnibus.omnibus.feature.bookdetail.stub

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.serialization.Serializable

/**
 * A pushed destination the Book Detail page links to before its real screen exists.
 *
 * @property title The name the placeholder shows, such as an author's name.
 */
@Serializable
data class BookDetailPlaceholderRoute(val title: String) : NavKey

/**
 * Placeholder for an unbuilt destination. It has no back button: system back pops it, as for any pushed entry.
 *
 * @param title The name shown large in the centre.
 */
@Composable
fun BookDetailPlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = displayTextStyle(27, palette.ink0),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.book_detail_placeholder_message),
            style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
        )
    }
}

@PreviewLightDark
@Composable
private fun BookDetailPlaceholderScreenPreview() {
    OmnibusTheme {
        BookDetailPlaceholderScreen(title = "Ursula K. Le Guin")
    }
}
