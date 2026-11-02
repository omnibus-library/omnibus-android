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
 * The Library's push destination for a listening resume. A stub until the audiobook player exists.
 *
 * @property bookUuid The book to play.
 * @property bookFileId The file the listener stopped in, or null when the player should pick one.
 */
@Serializable
data class PlayerStubRoute(val bookUuid: String, val bookFileId: Long?) : NavKey

/**
 * Placeholder player. It has no back button: system back pops it, as for any pushed entry.
 *
 * @param bookUuid The book the player will play.
 * @param bookFileId The saved file id, shown as "none" when absent.
 */
@Composable
fun PlayerStubScreen(bookUuid: String, bookFileId: Long?, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.player_stub_title),
            style = displayTextStyle(27, palette.ink0),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.player_stub_uuid, bookUuid),
            style = monoTextStyle(12, palette.ink3),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.player_stub_file_id,
                bookFileId?.toString() ?: stringResource(R.string.player_stub_no_file),
            ),
            style = monoTextStyle(12, palette.ink3),
        )
    }
}

@PreviewLightDark
@Composable
private fun PlayerStubScreenPreview() {
    OmnibusTheme {
        PlayerStubScreen(bookUuid = "7f3c2a9e-preview", bookFileId = 7L)
    }
}
