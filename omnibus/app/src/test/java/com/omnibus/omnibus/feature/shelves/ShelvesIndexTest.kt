package com.omnibus.omnibus.feature.shelves

import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.library.randomShelf
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** The owner of a shelf in a test: the user id, the username and whether the server has their picture. */
private data class Owner(val id: Long, val name: String, val hasAvatar: Boolean)

/** A shelf of [owner]'s with the given id, name, kind and visibility, and other facts random. */
private fun previewOf(
    id: Long,
    owner: Owner,
    name: String,
    kind: ShelfKind,
    visibility: ShelfVisibility,
): ShelfPreview = ShelfPreview(
    shelf = randomShelf().copy(
        id = id,
        ownerUserId = owner.id,
        ownerUsername = owner.name,
        ownerHasAvatar = owner.hasAvatar,
        name = name,
        kind = kind,
        visibility = visibility,
    ),
    coverUuids = emptyList(),
)

/** A shelf of [ownerId]'s with the given visibility, for counts and the notice. Its other facts are random. */
private fun previewOwnedBy(ownerId: Long, visibility: ShelfVisibility = ShelfVisibility.Public): ShelfPreview =
    ShelfPreview(
        shelf = randomShelf().copy(ownerUserId = ownerId, visibility = visibility),
        coverUuids = emptyList(),
    )

/**
 * Shelves from four owners, in no particular order. The viewer is owner 1, whose name sorts last among them.
 * Owner 10 and owner 20 differ only by case, and owners 20 and 30 share a username.
 */
private fun mixedShelves(): List<ShelfPreview> {
    val me = Owner(1L, "zoe", hasAvatar = false)
    val ann = Owner(10L, "Ann", hasAvatar = true)
    val annLower = Owner(20L, "ann", hasAvatar = false)
    val annAgain = Owner(30L, "ann", hasAvatar = false)
    val zed = Owner(40L, "Zed", hasAvatar = false)
    return listOf(
        previewOf(205, zed, "Zz", ShelfKind.Smart, ShelfVisibility.Private),
        previewOf(104, me, "gamma", ShelfKind.Wishlist, ShelfVisibility.Private),
        previewOf(201, ann, "dup", ShelfKind.Manual, ShelfVisibility.Public),
        previewOf(202, annLower, "Empty", ShelfKind.Wishlist, ShelfVisibility.Private),
        previewOf(101, me, "Beta", ShelfKind.Manual, ShelfVisibility.Private),
        previewOf(204, annAgain, "Reads", ShelfKind.Manual, ShelfVisibility.Private),
        previewOf(200, ann, "Dup", ShelfKind.Smart, ShelfVisibility.Public),
        previewOf(203, annLower, "cozy", ShelfKind.Manual, ShelfVisibility.Public),
        previewOf(103, me, "alpha", ShelfKind.Manual, ShelfVisibility.Public),
    )
}

private data class CensusCase(val previews: List<ShelfPreview>, val viewerId: Long?, val expected: ShelvesCensus)

private data class NoticeCase(val previews: List<ShelfPreview>, val viewerId: Long?, val expected: Boolean)

class ShelvesIndexTest : DescribeSpec({

    describe("grouping the shelves by owner") {

        it("#257 TC-5: keeps every shelf once") {
            val shelves = mixedShelves()

            shelfOwnerGroups(shelves, viewerId = 1L).flatMap { it.shelves } shouldContainExactlyInAnyOrder shelves
        }

        it("#257 TC-5: orders the groups with the viewer first, then by username ignoring case and owner id") {
            shelfOwnerGroups(mixedShelves(), viewerId = 1L).map { it.ownerId } shouldBe listOf(1L, 10L, 20L, 30L, 40L)
        }

        it("#257 TC-5: orders each group's shelves with wishlists last, then by name ignoring case and id") {
            shelfOwnerGroups(mixedShelves(), viewerId = 1L).map { group ->
                group.shelves.map { it.shelf.id }
            } shouldBe listOf(
                listOf(103L, 101L, 104L),
                listOf(200L, 201L),
                listOf(203L, 202L),
                listOf(204L),
                listOf(205L),
            )
        }

        it("#257 TC-5: carries each owner's name, avatar flag and viewer flag into the group") {
            shelfOwnerGroups(mixedShelves(), viewerId = 1L).map {
                Triple(it.ownerName, it.ownerHasAvatar, it.isViewer)
            } shouldBe listOf(
                Triple("zoe", false, true),
                Triple("Ann", true, false),
                Triple("ann", false, false),
                Triple("ann", false, false),
                Triple("Zed", false, false),
            )
        }

        context("when the viewer is unknown") {
            it("#257 TC-6: has no viewer group") {
                shelfOwnerGroups(mixedShelves(), viewerId = null).any { it.isViewer } shouldBe false
            }
        }

        context("when the viewer owns no shelves") {
            it("#257 TC-6: has no viewer group") {
                val shelves = mixedShelves().filter { it.shelf.ownerUserId != 1L }

                shelfOwnerGroups(shelves, viewerId = 1L).map { it.ownerId } shouldBe listOf(10L, 20L, 30L, 40L)
            }
        }
    }

    describe("counting the shelves") {
        it("#257 TC-7: counts total, yours, from others and other readers") {
            checkAll(
                Exhaustive.of(
                    CensusCase(emptyList(), viewerId = 1L, expected = ShelvesCensus(0, 0, 0, 0)),
                    CensusCase(
                        listOf(previewOwnedBy(1L), previewOwnedBy(1L)),
                        viewerId = 1L,
                        expected = ShelvesCensus(2, 2, 0, 0),
                    ),
                    CensusCase(
                        listOf(previewOwnedBy(2L), previewOwnedBy(2L), previewOwnedBy(3L)),
                        viewerId = 1L,
                        expected = ShelvesCensus(3, 0, 3, 2),
                    ),
                    CensusCase(
                        listOf(previewOwnedBy(1L), previewOwnedBy(2L), previewOwnedBy(2L)),
                        viewerId = 1L,
                        expected = ShelvesCensus(3, 1, 2, 1),
                    ),
                    CensusCase(
                        listOf(previewOwnedBy(1L), previewOwnedBy(2L)),
                        viewerId = null,
                        expected = ShelvesCensus(2, 0, 2, 2),
                    ),
                ),
            ) { case ->
                shelvesCensus(case.previews, case.viewerId) shouldBe case.expected
            }
        }
    }

    describe("showing the private-shelves notice") {
        it("#257 TC-8: shows the notice only for another reader's private shelf and a known viewer") {
            checkAll(
                Exhaustive.of(
                    NoticeCase(listOf(previewOwnedBy(2L, ShelfVisibility.Private)), viewerId = 1L, expected = true),
                    NoticeCase(listOf(previewOwnedBy(1L, ShelfVisibility.Private)), viewerId = 1L, expected = false),
                    NoticeCase(listOf(previewOwnedBy(2L, ShelfVisibility.Public)), viewerId = 1L, expected = false),
                    NoticeCase(listOf(previewOwnedBy(2L, ShelfVisibility.Private)), viewerId = null, expected = false),
                    NoticeCase(emptyList(), viewerId = 1L, expected = false),
                ),
            ) { case ->
                showsPrivateShelvesNotice(case.previews, case.viewerId) shouldBe case.expected
            }
        }
    }
})
