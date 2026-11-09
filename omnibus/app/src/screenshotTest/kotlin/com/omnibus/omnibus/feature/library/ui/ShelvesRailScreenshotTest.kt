package com.omnibus.omnibus.feature.library.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.screenshot.Shot

/**
 * One card per mosaic arrangement, then the plate, a smart public shelf of another user with a long name and meta line,
 * so the badges, the plate and the ellipsis all show.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 800)
@Preview(name = "Dark", widthDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelfCardVariants() {
    Shot {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShelfCard(shelf(1L, "Currently Reading", ShelfKind.Manual, ShelfVisibility.Private, 12L, covers = 4), viewerId = 7L)
            ShelfCard(shelf(2L, "Sci-Fi Favourites", ShelfKind.Smart, ShelfVisibility.Private, 34L, covers = 3), viewerId = 7L)
            ShelfCard(shelf(3L, "Book Club 2026", ShelfKind.Manual, ShelfVisibility.Public, 8L, covers = 2), viewerId = 7L)
            ShelfCard(shelf(4L, "Hunter's Wishlist", ShelfKind.Wishlist, ShelfVisibility.Private, 1L, covers = 1), viewerId = 7L)
            ShelfCard(shelf(5L, "Someday", ShelfKind.Manual, ShelfVisibility.Private, 0L, covers = 0), viewerId = 7L)
            ShelfCard(
                shelf(
                    6L,
                    "A very long shelf name that ellipsizes",
                    ShelfKind.Smart,
                    ShelfVisibility.Public,
                    bookCount = 9L,
                    covers = 2,
                    ownerId = 8L,
                    ownerName = "Annabelle Whitfield",
                ),
                viewerId = 7L,
            )
        }
    }
}

/** A shelf for the screenshots, with [covers] cover uuids that load nothing, so each cover draws as a plate. */
private fun shelf(
    id: Long,
    name: String,
    kind: ShelfKind,
    visibility: ShelfVisibility,
    bookCount: Long,
    covers: Int,
    ownerId: Long = 7L,
    ownerName: String = "Hunter",
) = ShelfPreview(
    shelf = Shelf(
        id = id,
        ownerUserId = ownerId,
        ownerUsername = ownerName,
        kind = kind,
        name = name,
        visibility = visibility,
        accent = null,
        bookCount = bookCount,
    ),
    coverUuids = List(covers) { "preview-$id-$it" },
)
