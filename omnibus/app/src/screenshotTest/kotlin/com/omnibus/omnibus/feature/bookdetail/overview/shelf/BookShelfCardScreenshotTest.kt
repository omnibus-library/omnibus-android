package com.omnibus.omnibus.feature.bookdetail.overview.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity

/** A card with a subtitle, and one without, which draws no subtitle line. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookShelfCards() {
    Shot {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookShelfCard(CoverIdentity(uuid = "uuid-1", title = "The Dispossessed", hasCover = false), "The Dispossessed", "1974", {}, null)
            BookShelfCard(CoverIdentity(uuid = "uuid-2", title = "The Lathe of Heaven", hasCover = false), "The Lathe of Heaven", null, {}, null)
        }
    }
}
