package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A provider's cover: the generated plate, with the provider's own art over it when it gave an http(s) address. The
 * plate is keyed by [title], since a provider record has no library uuid.
 */
@Composable
fun ProviderCover(title: String, creator: String?, coverUrl: String?, modifier: Modifier = Modifier) {
    val url = coverUrl?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    Box(modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp))) {
        BookCover(
            CoverIdentity(uuid = title, title = title, creator = creator, hasCover = false),
            Modifier.fillMaxWidth(),
            cornerRadius = 4.dp,
        )
        if (url != null && !LocalInspectionMode.current) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        }
    }
}
