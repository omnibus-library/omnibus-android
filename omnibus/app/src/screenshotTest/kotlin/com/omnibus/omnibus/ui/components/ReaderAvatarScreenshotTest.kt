package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

/** The initial on a disc at two sizes. Previews never load a picture, so this is the fallback a reader without one sees. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderAvatarInitials() {
    Shot {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReaderAvatar(userId = 3, name = "maren", hasAvatar = false, size = 32.dp)
            ReaderAvatar(userId = 4, name = "Theo", hasAvatar = false, size = 40.dp)
        }
    }
}
