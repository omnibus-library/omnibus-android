package com.omnibus.omnibus.feature.shelves.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Semantics and sizing of [ShelfTile]. ShelfTileScreenshotTest covers how the tiles look. */
class ShelfTileTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue257_tc17_eachTileIsOneNodeWithTheFullDescription() {
        setTiles(
            shelf(1L, name = "Compiler corner", kind = ShelfKind.Manual, visibility = ShelfVisibility.Private, bookCount = 3L),
            shelf(
                2L,
                name = "Sci-Fi Favourites",
                kind = ShelfKind.Smart,
                visibility = ShelfVisibility.Public,
                bookCount = 4L,
                ownerName = "bart",
            ),
            ownerLabels = mapOf(1L to "You", 2L to "bart"),
        )

        rule.onNodeWithTag(ShelfTileTestTags.tile(1L))
            .assert(hasContentDescriptionExactly("Compiler corner, 3 books, Hand-picked, Private, You"))
        rule.onNodeWithTag(ShelfTileTestTags.tile(2L))
            .assert(hasContentDescriptionExactly("Sci-Fi Favourites, 4 books, Smart, Public, bart"))
        // The text and the avatar are merged into the tile's one node, so the tile has no children of its own.
        rule.onNodeWithTag(ShelfTileTestTags.tile(1L)).onChildren().assertCountEquals(0)
        rule.onNodeWithTag(ShelfTileTestTags.tile(2L)).onChildren().assertCountEquals(0)
    }

    @Test
    fun issue257_tc17_aTileOpensItsShelf() {
        setTiles(shelf(1L), ownerLabels = mapOf(1L to "You"))

        rule.onNodeWithTag(ShelfTileTestTags.tile(1L))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    }

    @Test
    fun issue257_tc18_longTextNeverChangesTheTileSize() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth()) {
                        ShelfTile(
                            preview = shelf(1L, name = "Short", ownerName = "Ann"),
                            ownerLabel = "Ann",
                            modifier = Modifier.weight(1f),
                        )
                        ShelfTile(
                            preview = shelf(
                                2L,
                                name = "A very long shelf name that goes on well past any single line of the tile",
                                ownerName = "Annabelle Whitfield-Montgomery of the Long Name",
                                kind = ShelfKind.Smart,
                                visibility = ShelfVisibility.Public,
                                bookCount = 120L,
                            ),
                            ownerLabel = "Annabelle Whitfield-Montgomery of the Long Name",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        val short = rule.onNodeWithTag(ShelfTileTestTags.tile(1L)).fetchSemanticsNode().boundsInRoot
        val long = rule.onNodeWithTag(ShelfTileTestTags.tile(2L)).fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the two tiles are the same height", abs(short.height - long.height) < 1f)
        assertTrue("the long tile stays inside the window", long.right <= root.right + 1f)
    }

    private fun setTiles(vararg previews: ShelfPreview, ownerLabels: Map<Long, String>) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth()) {
                        previews.forEach { preview ->
                            ShelfTile(
                                preview = preview,
                                ownerLabel = ownerLabels.getValue(preview.shelf.id),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shelf(
        id: Long,
        name: String = "Shelf $id",
        kind: ShelfKind = ShelfKind.Manual,
        visibility: ShelfVisibility = ShelfVisibility.Private,
        bookCount: Long = 1L,
        ownerName: String = "maya",
    ) = ShelfPreview(
        shelf = Shelf(
            id = id,
            ownerUserId = if (ownerName == "maya") 7L else 8L,
            ownerUsername = ownerName,
            kind = kind,
            name = name,
            visibility = visibility,
            accent = null,
            bookCount = bookCount,
        ),
        coverUuids = emptyList(),
    )
}
