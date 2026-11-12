package com.omnibus.omnibus.feature.shelves.detail

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomShelf
import com.omnibus.omnibus.data.library.randomShelfPreview
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.data.shelves.FilterMode
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.data.shelves.randomShelfDetail
import com.omnibus.omnibus.feature.library.LibraryGridItem
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

/** Who is looking at a shelf, relative to its owner. */
private enum class Viewer { Owner, Admin, Other }

/** The viewer of [viewer] for [shelf]. */
private fun viewerFor(viewer: Viewer, shelf: Shelf): ShelfViewer = when (viewer) {
    Viewer.Owner -> ShelfViewer(userId = shelf.ownerUserId, isAdmin = false)
    Viewer.Admin -> ShelfViewer(userId = shelf.ownerUserId + 1, isAdmin = true)
    Viewer.Other -> ShelfViewer(userId = shelf.ownerUserId + 2, isAdmin = false)
}

/** A shelf of [kind] with a random identity, and [count] books on it. */
private fun shelfOf(kind: ShelfKind, count: Long = randomLong(0L..500L)): Shelf =
    randomShelf().copy(kind = kind, bookCount = count)

/** A screen state with [preview] and the given fields, everything else as a fresh screen. */
private fun stateWith(
    preview: ShelfPreview? = null,
    detail: ShelfDetail? = null,
    books: List<Book>? = null,
    unfilteredBooks: List<Book>? = null,
    pageError: ShelvesError? = null,
    pageNoServer: Boolean = false,
    clauses: List<FilterClause> = emptyList(),
    detailError: ShelvesError? = null,
    detailNoServer: Boolean = false,
    isGone: Boolean = false,
    unfoldedSeries: Set<Long> = emptySet(),
) = ShelfDetailUIState(
    shelfId = randomLong(),
    preview = preview,
    detail = detail,
    detailError = detailError,
    detailNoServer = detailNoServer,
    isGone = isGone,
    books = books,
    unfilteredBooks = unfilteredBooks,
    pageError = pageError,
    pageNoServer = pageNoServer,
    clauses = clauses,
    unfoldedSeries = unfoldedSeries,
)

private val genreFilter = listOf(FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy")))

class ShelfDetailRulesTest : DescribeSpec({

    describe("what the viewer may do") {

        it("#232 TC-7: edits a shelf only for its owner or an admin, never a wishlist") {
            checkAll(Exhaustive.enum<ShelfKind>(), Exhaustive.enum<Viewer>()) { kind, viewer ->
                val shelf = shelfOf(kind)

                shelfActionsOf(shelf, viewerFor(viewer, shelf)).canEdit shouldBe
                    (kind != ShelfKind.Wishlist && viewer != Viewer.Other)
            }
        }

        it("#232 TC-7: adds books only to a hand-picked shelf the viewer may edit") {
            checkAll(Exhaustive.enum<ShelfKind>(), Exhaustive.enum<Viewer>()) { kind, viewer ->
                val shelf = shelfOf(kind)

                shelfActionsOf(shelf, viewerFor(viewer, shelf)).canAddBooks shouldBe
                    (kind == ShelfKind.Manual && viewer != Viewer.Other)
            }
        }

        it("#232 TC-7: removes books only from a hand-picked shelf the viewer may edit") {
            checkAll(Exhaustive.enum<ShelfKind>(), Exhaustive.enum<Viewer>()) { kind, viewer ->
                val shelf = shelfOf(kind)

                shelfActionsOf(shelf, viewerFor(viewer, shelf)).canRemoveBooks shouldBe
                    (kind == ShelfKind.Manual && viewer != Viewer.Other)
            }
        }

        context("when the shelf or the viewer is unknown") {

            it("#232 TC-7: allows nothing") {
                checkAll(Exhaustive.enum<ShelfKind>()) { kind ->
                    val shelf = shelfOf(kind)
                    val nothing = ShelfActions(canEdit = false, canAddBooks = false, canRemoveBooks = false)

                    shelfActionsOf(null, ShelfViewer(randomLong(), isAdmin = true)) shouldBe nothing
                    shelfActionsOf(shelf, null) shouldBe nothing
                }
            }
        }
    }

    describe("which screen shows") {

        it("#232 TC-8: shows Gone when the shelf was answered 404") {
            stateWith(isGone = true).screen shouldBe ShelfDetailScreenState.Gone
        }

        context("when there is no shelf identity yet") {

            it("#232 TC-8: shows Loading while the detail read is pending") {
                stateWith().screen shouldBe ShelfDetailScreenState.Loading
            }

            it("#232 TC-8: shows Error with the failure when the detail read failed") {
                stateWith(detailError = ShelvesError.Unreachable).screen shouldBe
                    ShelfDetailScreenState.Error(ShelvesError.Unreachable)
            }

            it("#232 TC-8: shows Error without a failure when no server is saved") {
                stateWith(detailNoServer = true).screen shouldBe ShelfDetailScreenState.Error(null)
            }
        }

        it("#232 TC-8: shows Content once the shelf has an identity") {
            stateWith(preview = randomShelfPreview()).screen shouldBe ShelfDetailScreenState.Content
        }
    }

    describe("what fills the shelf") {

        it("#232 TC-8: shows Loading while the page read is pending") {
            stateWith(preview = randomShelfPreview()).content shouldBe ShelfContentState.Loading
        }

        context("when the page is unreachable and nothing is cached") {

            it("#232 TC-8: shows Unreachable with the shelf's count when it holds books") {
                val count = randomLong(1L..500L)
                val preview = ShelfPreview(shelfOf(ShelfKind.Manual, count), emptyList())

                stateWith(preview = preview, pageError = ShelvesError.Unreachable).content shouldBe
                    ShelfContentState.Unreachable(count)
            }

            it("#232 TC-8: shows Failed, not Unreachable, when the shelf holds no books") {
                val preview = ShelfPreview(shelfOf(ShelfKind.Manual, 0L), emptyList())

                stateWith(preview = preview, pageError = ShelvesError.Unreachable).content shouldBe
                    ShelfContentState.Failed(ShelvesError.Unreachable)
            }
        }

        it("#232 TC-8: shows Failed with the error for another page failure") {
            val preview = randomShelfPreview()

            stateWith(preview = preview, pageError = ShelvesError.Http(500, "boom")).content shouldBe
                ShelfContentState.Failed(ShelvesError.Http(500, "boom"))
        }

        it("#232 TC-8: shows Failed without an error when no server is saved") {
            stateWith(preview = randomShelfPreview(), pageNoServer = true).content shouldBe
                ShelfContentState.Failed(null)
        }

        it("#232 TC-8: shows Empty for the shelf's kind when the page has no books") {
            checkAll(Exhaustive.enum<ShelfKind>()) { kind ->
                val preview = ShelfPreview(shelfOf(kind), emptyList())

                stateWith(preview = preview, books = emptyList()).content shouldBe ShelfContentState.Empty(kind)
            }
        }

        it("#232 TC-8: shows Books when the page has books") {
            stateWith(preview = randomShelfPreview(), books = listOf(randomBook())).content shouldBe
                ShelfContentState.Books
        }
    }

    describe("a filtered shelf") {

        it("#232 TC-9: shows FilteredEmpty when a filtered page has no books, not the unfiltered Empty") {
            val preview = ShelfPreview(shelfOf(ShelfKind.Manual), emptyList())

            stateWith(preview = preview, clauses = genreFilter, books = emptyList()).content shouldBe
                ShelfContentState.FilteredEmpty
        }

        context("when the filtered read failed with nothing cached") {

            it("#232 TC-9: shows FilterFailed as unreachable for an unreachable read") {
                stateWith(
                    preview = randomShelfPreview(),
                    clauses = genreFilter,
                    pageError = ShelvesError.Unreachable,
                ).content shouldBe ShelfContentState.FilterFailed(unreachable = true, error = ShelvesError.Unreachable)
            }

            it("#232 TC-9: shows FilterFailed with the error for another failure") {
                stateWith(
                    preview = randomShelfPreview(),
                    clauses = genreFilter,
                    pageError = ShelvesError.Http(500, "boom"),
                ).content shouldBe
                    ShelfContentState.FilterFailed(unreachable = false, error = ShelvesError.Http(500, "boom"))
            }
        }

        it("#232 TC-9: shows Books for a filtered page with books") {
            stateWith(preview = randomShelfPreview(), clauses = genreFilter, books = listOf(randomBook())).content shouldBe
                ShelfContentState.Books
        }
    }

    describe("grouping the shelf's books") {

        it("#232 TC-10: shows two books of one series as a stack") {
            val series = randomLong()
            val books = listOf(
                randomBook().copy(seriesId = series, seriesIndex = "1"),
                randomBook().copy(seriesId = series, seriesIndex = "2"),
            )

            val stack = stateWith(books = books).gridItems.single().shouldBeTypeOf<LibraryGridItem.Stack>()

            stack.group.count shouldBe 2
        }

        it("#232 TC-10: shows a lone series book as a plain cell") {
            val book = randomBook().copy(seriesId = randomLong())

            stateWith(books = listOf(book)).gridItems shouldBe listOf(LibraryGridItem.Single(book))
        }

        it("#232 TC-10: shows a book without a series as a plain cell") {
            val books = List(randomInt(1..4)) { randomBook().copy(seriesId = null) }

            stateWith(books = books).gridItems shouldBe books.map { LibraryGridItem.Single(it) }
        }

        it("#232 TC-10: keeps the server's order") {
            val books = List(randomInt(2..5)) { randomBook().copy(seriesId = null) }

            stateWith(books = books).gridItems shouldBe books.map { LibraryGridItem.Single(it) }
        }

        it("#232 TC-10: lists the members of an unfolded stack in place") {
            val series = randomLong()
            val first = randomBook().copy(seriesId = series, seriesIndex = "1")
            val lone = randomBook().copy(seriesId = null)
            val second = randomBook().copy(seriesId = series, seriesIndex = "2")

            stateWith(books = listOf(first, lone, second), unfoldedSeries = setOf(series)).gridItems.map { it.key } shouldBe
                listOf("series-$series", "member-$series-${first.id}", "member-$series-${second.id}", lone.id)
        }
    }

    describe("what the hero shows") {

        it("#232 TC-11: takes the identity from the cached preview until the detail lands") {
            val preview = randomShelfPreview()

            stateWith(preview = preview).identity shouldBe preview.shelf
        }

        it("#232 TC-11: takes the identity from the detail once it lands") {
            val fresh = randomShelfDetail()

            stateWith(preview = randomShelfPreview(), detail = fresh).identity shouldBe fresh.shelf
        }

        context("when the unfiltered page has loaded") {

            it("#232 TC-11: takes the covers from the first four books with covers") {
                val books = List(6) { randomBook().copy(coverUrl = randomString()) }

                stateWith(preview = randomShelfPreview(), unfilteredBooks = books).mosaicCovers shouldBe
                    books.take(4).map { it.uuid }
            }

            it("#232 TC-11: skips books without covers") {
                val covered = List(2) { randomBook().copy(coverUrl = randomString()) }
                val books = listOf(randomBook().copy(coverUrl = null)) + covered

                stateWith(preview = randomShelfPreview(), unfilteredBooks = books).mosaicCovers shouldBe
                    covered.map { it.uuid }
            }
        }

        it("#232 TC-11: a filtered page never changes the covers") {
            val unfiltered = List(2) { randomBook().copy(coverUrl = randomString()) }
            val filtered = List(1) { randomBook().copy(coverUrl = randomString()) }

            stateWith(
                preview = randomShelfPreview(),
                unfilteredBooks = unfiltered,
                books = filtered,
                clauses = genreFilter,
            ).mosaicCovers shouldBe unfiltered.map { it.uuid }
        }

        it("#232 TC-11: falls back to the preview's covers before the page has loaded") {
            val covers = List(5) { randomString() }
            val preview = ShelfPreview(randomShelf(), covers)

            stateWith(preview = preview).mosaicCovers shouldBe covers.take(4)
        }

        it("#232 TC-11: has no covers when neither the page nor the preview has any") {
            stateWith(preview = ShelfPreview(randomShelf(), emptyList())).mosaicCovers shouldBe emptyList()
        }

        it("#232 TC-11: lists the count, kind and visibility for any count, kind and visibility") {
            checkAll(
                Exhaustive.of(0L, 1L, 5L),
                Exhaustive.enum<ShelfKind>(),
                Exhaustive.enum<ShelfVisibility>(),
            ) { count, kind, visibility ->
                val shelf = randomShelf().copy(bookCount = count, kind = kind, visibility = visibility)

                shelfDetailMetaParts(shelf) shouldBe listOf(
                    ShelfDetailMetaPart.BookCount(count),
                    ShelfDetailMetaPart.Kind(kind),
                    ShelfDetailMetaPart.Visibility(visibility),
                )
            }
        }
    }
})
