package com.omnibus.omnibus.feature.libraries.card

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.Spacing

/** A library with no letter in its name, so the tile holds the Maltese cross and the account line is the host alone. */
@PreviewTest
@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AccountCardMalteseCross() {
    Shot {
        Box(Modifier.padding(Spacing.screen)) {
            AccountCard(
                Library(
                    id = "cross",
                    address = "http://192.168.1.5:8080",
                    name = "",
                    username = null,
                    addedAt = 0,
                    lastUsedAt = 0,
                    isActive = true,
                ),
            )
        }
    }
}

/** The largest font scale on a narrow window: the name stays one line and the account line wraps to two at most. */
@PreviewTest
@Preview(name = "Light", widthDp = 320, fontScale = 2f)
@Preview(name = "Dark", widthDp = 320, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AccountCardLargeFontNarrow() {
    Shot {
        Box(Modifier.padding(Spacing.screen)) {
            AccountCard(
                Library(
                    id = "long",
                    address = "https://library.books.example-household.lan:8443",
                    name = "The Extraordinarily Long Library of Hoon",
                    username = "hoon-with-a-long-username",
                    addedAt = 0,
                    lastUsedAt = 0,
                    isActive = true,
                ),
            )
        }
    }
}
