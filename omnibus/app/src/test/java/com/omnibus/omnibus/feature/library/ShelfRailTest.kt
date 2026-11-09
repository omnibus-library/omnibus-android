package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.library.randomShelf
import com.omnibus.omnibus.data.library.randomShelfPreview
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

private enum class RailViewer { Unknown, Owner, Someone }

/** One combination of shelf facts, with whether the rail keeps it for [viewer]. */
private data class RailRow(
    val viewer: RailViewer,
    val kind: ShelfKind,
    val visibility: ShelfVisibility,
    val bookCount: Long,
    val kept: Boolean,
)

class ShelfRailTest : DescribeSpec({

    describe("the rail shelves") {

        it("TC-4: keeps or drops a shelf by viewer, kind, visibility and book count") {
            val rows = listOf(
                // No signed-in viewer: only public shelves that aren't wishlists.
                RailRow(RailViewer.Unknown, ShelfKind.Smart, ShelfVisibility.Private, 0L, kept = false),
                RailRow(RailViewer.Unknown, ShelfKind.Smart, ShelfVisibility.Private, 2L, kept = false),
                RailRow(RailViewer.Unknown, ShelfKind.Smart, ShelfVisibility.Public, 0L, kept = true),
                RailRow(RailViewer.Unknown, ShelfKind.Smart, ShelfVisibility.Public, 2L, kept = true),
                RailRow(RailViewer.Unknown, ShelfKind.Manual, ShelfVisibility.Private, 0L, kept = false),
                RailRow(RailViewer.Unknown, ShelfKind.Manual, ShelfVisibility.Private, 2L, kept = false),
                RailRow(RailViewer.Unknown, ShelfKind.Manual, ShelfVisibility.Public, 0L, kept = true),
                RailRow(RailViewer.Unknown, ShelfKind.Manual, ShelfVisibility.Public, 2L, kept = true),
                RailRow(RailViewer.Unknown, ShelfKind.Wishlist, ShelfVisibility.Private, 0L, kept = false),
                RailRow(RailViewer.Unknown, ShelfKind.Wishlist, ShelfVisibility.Private, 2L, kept = false),
                RailRow(RailViewer.Unknown, ShelfKind.Wishlist, ShelfVisibility.Public, 0L, kept = false),
                RailRow(RailViewer.Unknown, ShelfKind.Wishlist, ShelfVisibility.Public, 2L, kept = false),
                // The owner: every shelf except an empty wishlist.
                RailRow(RailViewer.Owner, ShelfKind.Smart, ShelfVisibility.Private, 0L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Smart, ShelfVisibility.Private, 2L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Smart, ShelfVisibility.Public, 0L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Smart, ShelfVisibility.Public, 2L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Manual, ShelfVisibility.Private, 0L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Manual, ShelfVisibility.Private, 2L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Manual, ShelfVisibility.Public, 0L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Manual, ShelfVisibility.Public, 2L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Wishlist, ShelfVisibility.Private, 0L, kept = false),
                RailRow(RailViewer.Owner, ShelfKind.Wishlist, ShelfVisibility.Private, 2L, kept = true),
                RailRow(RailViewer.Owner, ShelfKind.Wishlist, ShelfVisibility.Public, 0L, kept = false),
                RailRow(RailViewer.Owner, ShelfKind.Wishlist, ShelfVisibility.Public, 2L, kept = true),
                // Another user: the same as no viewer, since nothing they see is their own.
                RailRow(RailViewer.Someone, ShelfKind.Smart, ShelfVisibility.Private, 0L, kept = false),
                RailRow(RailViewer.Someone, ShelfKind.Smart, ShelfVisibility.Private, 2L, kept = false),
                RailRow(RailViewer.Someone, ShelfKind.Smart, ShelfVisibility.Public, 0L, kept = true),
                RailRow(RailViewer.Someone, ShelfKind.Smart, ShelfVisibility.Public, 2L, kept = true),
                RailRow(RailViewer.Someone, ShelfKind.Manual, ShelfVisibility.Private, 0L, kept = false),
                RailRow(RailViewer.Someone, ShelfKind.Manual, ShelfVisibility.Private, 2L, kept = false),
                RailRow(RailViewer.Someone, ShelfKind.Manual, ShelfVisibility.Public, 0L, kept = true),
                RailRow(RailViewer.Someone, ShelfKind.Manual, ShelfVisibility.Public, 2L, kept = true),
                RailRow(RailViewer.Someone, ShelfKind.Wishlist, ShelfVisibility.Private, 0L, kept = false),
                RailRow(RailViewer.Someone, ShelfKind.Wishlist, ShelfVisibility.Private, 2L, kept = false),
                RailRow(RailViewer.Someone, ShelfKind.Wishlist, ShelfVisibility.Public, 0L, kept = false),
                RailRow(RailViewer.Someone, ShelfKind.Wishlist, ShelfVisibility.Public, 2L, kept = false),
            )
            val owner = randomLong()

            checkAll(Exhaustive.collection(rows)) { row ->
                val viewerId = when (row.viewer) {
                    RailViewer.Unknown -> null
                    RailViewer.Owner -> owner
                    RailViewer.Someone -> owner + 1
                }
                val preview = randomShelfPreview().copy(
                    shelf = randomShelf().copy(
                        ownerUserId = owner,
                        kind = row.kind,
                        visibility = row.visibility,
                        bookCount = row.bookCount,
                    ),
                )

                railShelvesOf(listOf(preview), viewerId) shouldBe if (row.kept) listOf(preview) else emptyList()
            }
        }

        it("TC-5: keeps server order and never returns more than 8") {
            val viewer = randomLong()

            checkAll(Arb.list(Arb.boolean(), 0..30)) { eligibility ->
                val previews = eligibility.mapIndexed { index, eligible -> previewFor(index, eligible, viewer) }

                railShelvesOf(previews, viewer) shouldBe
                    previews.filterIndexed { index, _ -> eligibility[index] }.take(8)
            }
        }
    }

    describe("the owner attribution") {

        it("TC-6: attributes only another user's non-wishlist shelf") {
            val owner = randomLong()

            checkAll(Exhaustive.enum<ShelfKind>()) { kind ->
                showsOwnerAttribution(null, owner, kind) shouldBe false
                showsOwnerAttribution(owner, owner, kind) shouldBe false
                showsOwnerAttribution(owner + 1, owner, kind) shouldBe (kind != ShelfKind.Wishlist)
            }
        }
    }

    describe("the meta parts") {

        it("TC-7: lists count, Smart, Public and owner in order") {
            val owner = randomLong()

            checkAll(
                Exhaustive.enum<ShelfKind>(),
                Exhaustive.enum<ShelfVisibility>(),
                Exhaustive.of(owner, owner + 1),
                Exhaustive.of(0L, 1L, randomLong(2L..500L)),
            ) { kind, visibility, viewer, count ->
                val shelf = randomShelf().copy(ownerUserId = owner, kind = kind, visibility = visibility, bookCount = count)

                shelfMetaParts(shelf, viewer) shouldBe listOfNotNull(
                    ShelfMetaPart.BookCount(count),
                    ShelfMetaPart.Smart.takeIf { kind == ShelfKind.Smart },
                    ShelfMetaPart.Public.takeIf { visibility == ShelfVisibility.Public },
                    ShelfMetaPart.Owner(shelf.ownerUsername).takeIf { viewer != owner && kind != ShelfKind.Wishlist },
                )
            }
        }
    }

    describe("the mosaic") {

        it("TC-8: arranges the first four covers by count") {
            checkAll(
                Exhaustive.collection(
                    listOf(
                        0 to MosaicArrangement.Plate,
                        1 to MosaicArrangement.Single,
                        2 to MosaicArrangement.SideBySide,
                        3 to MosaicArrangement.LeadAndStack,
                    ) + (4..8).map { it to MosaicArrangement.Grid },
                ),
            ) { (count, arrangement) ->
                val uuids = List(count) { randomString() }

                shelfMosaicOf(uuids) shouldBe ShelfMosaic(arrangement, uuids.take(4))
            }
        }
    }

    describe("the plate tone") {

        context("with a valid accent") {
            it("TC-9: uses the accent, else the name hash") {
                checkAll(Exhaustive.of("#c8a04a", "oklch(0.6 0.1 200)")) { accent ->
                    shelfPlateTone(randomShelf().copy(accent = accent)) shouldBe Oklch.parse(accent)
                }
            }
        }

        context("with no accent") {
            it("TC-9: uses the accent, else the name hash") {
                val shelf = randomShelf().copy(accent = null)

                shelfPlateTone(shelf) shouldBe Oklch(0.55, 0.07, CoverIdentity.titleHue(shelf.name))
            }
        }

        context("with an accent that doesn't parse") {
            it("TC-9: uses the accent, else the name hash") {
                checkAll(Exhaustive.of("", "blue", "#12345", "oklch(0.5)")) { accent ->
                    val shelf = randomShelf().copy(accent = accent)

                    shelfPlateTone(shelf) shouldBe Oklch(0.55, 0.07, CoverIdentity.titleHue(shelf.name))
                }
            }
        }
    }
})

/**
 * A preview with a unique id. The owner is [viewer] with books when [eligible], so the rail keeps it whatever its kind
 * or visibility; otherwise it's another user's private manual shelf, which the rail never keeps.
 */
private fun previewFor(index: Int, eligible: Boolean, viewer: Long) = randomShelfPreview().copy(
    shelf = if (eligible) {
        randomShelf().copy(id = index.toLong(), ownerUserId = viewer, bookCount = randomLong(1L..500L))
    } else {
        randomShelf().copy(
            id = index.toLong(),
            ownerUserId = viewer + 1,
            kind = ShelfKind.Manual,
            visibility = ShelfVisibility.Private,
        )
    },
)
