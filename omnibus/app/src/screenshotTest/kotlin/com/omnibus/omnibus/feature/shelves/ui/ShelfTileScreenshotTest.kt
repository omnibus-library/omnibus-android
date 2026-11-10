package com.omnibus.omnibus.feature.shelves.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
 * Four tiles by how many covers stand on the ledge: four, three with the last leaning, one, and blank plates for a shelf
 * with books but no cover uuids. Two of them take the shelf's own accent, the others the app's.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 760)
@Preview(name = "Dark", widthDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelfTileCovers() {
    Shot {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShelfTile(
                preview = shelf(1L, "Four covers", ShelfKind.Manual, ShelfVisibility.Private, 62L, covers = 4),
                ownerLabel = "You",
                modifier = Modifier.width(168.dp),
            )
            ShelfTile(
                preview = shelf(2L, "Three covers", ShelfKind.Manual, ShelfVisibility.Private, 3L, covers = 3, accent = "#5b8def"),
                ownerLabel = "You",
                modifier = Modifier.width(168.dp),
            )
            ShelfTile(
                preview = shelf(3L, "One cover", ShelfKind.Manual, ShelfVisibility.Public, 1L, covers = 1),
                ownerLabel = "You",
                modifier = Modifier.width(168.dp),
            )
            ShelfTile(
                preview = shelf(4L, "Blank plates", ShelfKind.Smart, ShelfVisibility.Public, 14L, covers = 0),
                ownerLabel = "maya",
                modifier = Modifier.width(168.dp),
            )
        }
    }
}

/**
 * The three empty ledges, each with its copy, and a wishlist with books: public and private, the smart and wishlist
 * facets, and the "You" foot beside another owner's name.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 760)
@Preview(name = "Dark", widthDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelfTileEmptyAndFacets() {
    Shot {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShelfTile(
                preview = shelf(5L, "Empty manual", ShelfKind.Manual, ShelfVisibility.Private, 0L, covers = 0),
                ownerLabel = "You",
                modifier = Modifier.width(168.dp),
            )
            ShelfTile(
                preview = shelf(6L, "Empty wishlist", ShelfKind.Wishlist, ShelfVisibility.Private, 0L, covers = 0),
                ownerLabel = "You",
                modifier = Modifier.width(168.dp),
            )
            ShelfTile(
                preview = shelf(7L, "Empty smart", ShelfKind.Smart, ShelfVisibility.Public, 0L, covers = 0, ownerName = "maya"),
                ownerLabel = "maya",
                modifier = Modifier.width(168.dp),
            )
            ShelfTile(
                preview = shelf(8L, "Hunter's wishlist", ShelfKind.Wishlist, ShelfVisibility.Public, 2L, covers = 2, ownerName = "maya"),
                ownerLabel = "maya",
                modifier = Modifier.width(168.dp),
            )
        }
    }
}

/**
 * Text that doesn't fit: a long name and owner ellipsised in a 156 dp tile, beside a short one of the same width, which
 * has the same height. Below, a 156 dp tile where PRIVATE collapses to the lock, beside a 300 dp tile where both words
 * show.
 */
@PreviewTest
@Preview(name = "Light", widthDp = 520)
@Preview(name = "Dark", widthDp = 520, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShelfTileConstrained() {
    Shot {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTile(
                    preview = shelf(
                        9L,
                        "A very long shelf name that ellipsizes",
                        ShelfKind.Smart,
                        ShelfVisibility.Public,
                        9L,
                        covers = 2,
                        ownerName = "Annabelle Whitfield",
                    ),
                    ownerLabel = "Annabelle Whitfield",
                    modifier = Modifier.width(156.dp),
                )
                ShelfTile(
                    preview = shelf(10L, "Short", ShelfKind.Smart, ShelfVisibility.Public, 9L, covers = 2, ownerName = "Ann"),
                    ownerLabel = "Ann",
                    modifier = Modifier.width(156.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTile(
                    preview = shelf(11L, "Narrow facets", ShelfKind.Manual, ShelfVisibility.Private, 3L, covers = 3),
                    ownerLabel = "You",
                    modifier = Modifier.width(156.dp),
                )
                ShelfTile(
                    preview = shelf(12L, "Wide facets", ShelfKind.Manual, ShelfVisibility.Private, 3L, covers = 3),
                    ownerLabel = "You",
                    modifier = Modifier.width(300.dp),
                )
            }
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
    accent: String? = null,
    ownerName: String = "Hunter",
) = ShelfPreview(
    shelf = Shelf(
        id = id,
        ownerUserId = if (ownerName == "Hunter") 7L else 8L,
        ownerUsername = ownerName,
        kind = kind,
        name = name,
        visibility = visibility,
        accent = accent,
        bookCount = bookCount,
    ),
    coverUuids = List(covers) { "preview-$id-$it" },
)
