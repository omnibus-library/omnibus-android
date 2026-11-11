package com.omnibus.omnibus.feature.requests

import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.StuckReason
import com.omnibus.omnibus.data.requests.randomRequestListing
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private val CHOOSER = RequestViewer(id = 1, canStart = true, canChoose = true)
private val ASKER = RequestViewer(id = 1, canStart = true, canChoose = false)

/** A listed request for the book [uuid]. Only what the rules read is pinned. */
private fun listing(
    uuid: String,
    state: RequestState = RequestState.Looking,
    id: Long = 1,
    kind: RequestKind = RequestKind.Ebook,
    progress: Double? = null,
    updatedAt: Long = 0,
    stuckReason: StuckReason? = null,
): RequestListing = randomRequestListing().let {
    it.copy(
        request = it.request.copy(
            id = id, bookUuid = uuid, state = state, kind = kind, progress = progress, updatedAt = updatedAt, stuckReason = stuckReason,
        ),
        book = randomBook().copy(uniqueIdentifier = uuid),
    )
}

/** Each group's key and its books' uuids, in order. */
private fun List<RequestGroup>.shape() = map { group -> group.key to group.items.map { it.book.uuid } }

class RequestGroupsTest : DescribeSpec({

    describe("grouping the requests") {

        it("orders the groups needs you, on its way, looking, stuck, ready, then cancelled") {
            val groups = groupRequests(
                listOf(
                    listing("gone", RequestState.Cancelled, id = 0),
                    listing("ready", RequestState.Ready, id = 1),
                    listing("stuck", RequestState.Stuck, id = 2),
                    listing("looking", RequestState.Looking, id = 3),
                    listing("coming", RequestState.OnItsWay, id = 4),
                    listing("yours", RequestState.Choose, id = 5),
                ),
                CHOOSER,
            )

            groups.shape() shouldBe listOf(
                RequestGroupKey.NeedsYou to listOf("yours"),
                RequestGroupKey.OnItsWay to listOf("coming"),
                RequestGroupKey.Looking to listOf("looking"),
                RequestGroupKey.Stuck to listOf("stuck"),
                RequestGroupKey.Ready to listOf("ready"),
                RequestGroupKey.Cancelled to listOf("gone"),
            )
        }

        it("lists a picked version as on its way") {
            groupRequests(listOf(listing("b", RequestState.Chosen)), CHOOSER).shape() shouldBe
                listOf(RequestGroupKey.OnItsWay to listOf("b"))
        }

        context("when a pick is still to make") {
            val picks = listOf(listing("mine", RequestState.Choose, id = 1), listing("theirs", RequestState.Choose, id = 2))

            it("files it under looking for a reader who can't pick versions, their own included") {
                groupRequests(picks, ASKER).shape() shouldBe listOf(RequestGroupKey.Looking to listOf("mine", "theirs"))
            }

            it("files it under needs you for a reader who can pick versions") {
                groupRequests(picks, CHOOSER).shape() shouldBe listOf(RequestGroupKey.NeedsYou to listOf("mine", "theirs"))
            }
        }

        it("files a book whose every request was cancelled under cancelled") {
            groupRequests(listOf(listing("gone", RequestState.Cancelled)), CHOOSER).shape() shouldBe
                listOf(RequestGroupKey.Cancelled to listOf("gone"))
        }

        it("leaves no empty group") {
            groupRequests(emptyList(), CHOOSER) shouldBe emptyList()
        }
    }

    describe("one item per book") {

        it("files a book under its live request when a newer one was cancelled") {
            val groups = groupRequests(
                listOf(listing("b", RequestState.Cancelled, id = 4), listing("b", RequestState.Stuck, id = 3)),
                CHOOSER,
            )

            groups.shape() shouldBe listOf(RequestGroupKey.Stuck to listOf("b"))
        }

        it("files an all-cancelled book under the request cancelled last") {
            val groups = groupRequests(
                listOf(
                    listing("b", RequestState.Cancelled, id = 5, updatedAt = 1000),
                    listing("b", RequestState.Cancelled, id = 3, updatedAt = 2000),
                ),
                CHOOSER,
            )

            groups.single().items.single().request.id shouldBe 3
        }

        it("files a book under its most urgent request") {
            val groups = groupRequests(
                listOf(
                    listing("b", RequestState.Looking, id = 3),
                    listing("b", RequestState.OnItsWay, id = 2),
                    listing("b", RequestState.Cancelled, id = 1),
                ),
                CHOOSER,
            )

            groups.single().items.single().request.id shouldBe 2
        }

        it("files a book under its newest request when two are as urgent") {
            val groups = groupRequests(
                listOf(listing("b", RequestState.Looking, id = 3), listing("b", RequestState.Looking, id = 2)),
                CHOOSER,
            )

            groups.single().items.single().request.id shouldBe 3
        }

        it("keeps a book in the slot of its newest listing, not of the request it is filed under") {
            // "a" appears first as a cancelled listing, then as the live one it is filed under; "b" sits between.
            val groups = groupRequests(
                listOf(
                    listing("a", RequestState.Looking, id = 5),
                    listing("b", RequestState.Looking, id = 4),
                    listing("a", RequestState.Looking, id = 3),
                    listing("c", RequestState.Looking, id = 2),
                    listing("b", RequestState.OnItsWay, id = 1),
                ),
                CHOOSER,
            )

            groups.shape() shouldBe listOf(
                RequestGroupKey.OnItsWay to listOf("b"),
                RequestGroupKey.Looking to listOf("a", "c"),
            )
        }

        it("keeps a book in its first slot when a later listing of it replaces the one it was filed under") {
            val groups = groupRequests(
                listOf(
                    listing("b", RequestState.Cancelled, id = 6, updatedAt = 1000),
                    listing("a", RequestState.Cancelled, id = 5, updatedAt = 500),
                    listing("b", RequestState.Cancelled, id = 4, updatedAt = 2000),
                ),
                CHOOSER,
            )

            groups.single().items.map { it.request.id } shouldBe listOf(4L, 5L)
        }
    }

    describe("a row's second line") {

        it("reads where the request stands, in the phone's words") {
            checkAll(
                Exhaustive.of(
                    Triple(RequestGroupKey.NeedsYou, listing("b", RequestState.Choose), "Ebook · choose a version"),
                    Triple(RequestGroupKey.Looking, listing("b", RequestState.Choose), "Ebook · choosing a version"),
                    Triple(RequestGroupKey.Looking, listing("b", RequestState.Looking, kind = RequestKind.Audiobook), "Audiobook · looking for a version"),
                    Triple(RequestGroupKey.OnItsWay, listing("b", RequestState.Chosen), "Ebook · on its way"),
                    Triple(RequestGroupKey.OnItsWay, listing("b", RequestState.OnItsWay, progress = 0.62), "Ebook · 62%"),
                    Triple(RequestGroupKey.OnItsWay, listing("b", RequestState.OnItsWay, progress = null), "Ebook · on its way"),
                    Triple(RequestGroupKey.Stuck, listing("b", RequestState.Stuck, stuckReason = StuckReason.NotFound), "Ebook · couldn’t find it"),
                    Triple(RequestGroupKey.Stuck, listing("b", RequestState.Stuck, stuckReason = StuckReason.StoppedMoving), "Ebook · stopped moving"),
                    Triple(RequestGroupKey.Ready, listing("b", RequestState.Ready), "Ebook · ready to read"),
                    Triple(RequestGroupKey.Ready, listing("b", RequestState.Ready, kind = RequestKind.Audiobook), "Audiobook · ready to listen"),
                    Triple(RequestGroupKey.Cancelled, listing("b", RequestState.Cancelled), "Ebook · cancelled"),
                ),
            ) { (group, listing, line) -> itemLine(listing, group) shouldBe line }
        }

        it("tones each group's dot") {
            RequestGroupKey.entries.map { it.tone } shouldBe listOf(
                RequestTone.Accent, RequestTone.Ink1, RequestTone.Ink1, RequestTone.Bad, RequestTone.Ok, RequestTone.Ink3,
            )
        }
    }

    describe("the counts line") {
        fun groupsOf(vararg states: RequestState) =
            groupRequests(states.mapIndexed { i, state -> listing("b$i", state, id = i + 1L) }, CHOOSER)

        it("counts the books, naming those waiting on the viewer") {
            checkAll(
                Exhaustive.of(
                    groupsOf(RequestState.Choose, RequestState.Looking) to "2 books · 1 needs you",
                    groupsOf(RequestState.Choose, RequestState.Choose) to "2 books · 2 need you",
                    groupsOf(RequestState.Looking) to "1 book",
                    groupsOf(RequestState.Cancelled) to "0 books",
                    groupsOf(RequestState.Looking, RequestState.Cancelled) to "1 book",
                ),
            ) { (groups, line) -> countsLine(groups) shouldBe line }
        }
    }

    describe("the You tab's count") {
        it("counts the books waiting on a chooser to pick, and none for an asker") {
            val listings = listOf(listing("a", RequestState.Choose, id = 1), listing("b", RequestState.Choose, id = 2), listing("c", RequestState.Looking, id = 3))

            listOf(needsYouCount(listings, CHOOSER), needsYouCount(listings, ASKER)) shouldBe listOf(2, 0)
        }

        it("words the pill, and shows none for zero") {
            listOf(needsYouPill(0), needsYouPill(1), needsYouPill(2)) shouldBe listOf(null, "1 needs you", "2 need you")
        }
    }

    describe("when the list reads again") {
        it("polls every ten seconds while anything moves, and not otherwise") {
            listOf(
                listPollIntervalMillis(listOf(listing("a", RequestState.Ready), listing("b", RequestState.OnItsWay))),
                listPollIntervalMillis(listOf(listing("a", RequestState.Choose), listing("b", RequestState.Stuck))),
                listPollIntervalMillis(emptyList()),
                listPollIntervalMillis(null),
            ) shouldBe listOf(10_000L, null, null, null)
        }

        it("folds only Cancelled to start with") {
            DefaultFolded shouldBe setOf(RequestGroupKey.Cancelled)
        }
    }

    describe("the Request a book sheet's rows") {
        it("collects each book with an ebook request still going") {
            val listings = listOf(
                listing("going", RequestState.Looking),
                listing("stuck", RequestState.Stuck),
                listing("ready", RequestState.Ready),
                listing("gone", RequestState.Cancelled),
                listing("audio", RequestState.Looking, kind = RequestKind.Audiobook),
            )

            requestedBooks(listings) shouldBe setOf("going", "stuck")
        }

        it("opens a requested book, says an ebook is held, or starts a request") {
            val requested = setOf("a")

            listOf(
                addAction("a", listOf("EPUB"), true, requested),
                addAction("b", listOf("EPUB"), true, requested),
                addAction("b", listOf("M4B"), true, requested),
                addAction("b", listOf("PRINT"), false, requested),
            ) shouldBe listOf(AddAction.Open, AddAction.Held, AddAction.Start, AddAction.Start)
        }

        it("counts any file as an ebook when the lookup sent no formats") {
            listOf(addAction("b", null, true, emptySet()), addAction("b", null, false, emptySet())) shouldBe
                listOf(AddAction.Held, AddAction.Start)
        }
    }

    describe("the request a page opens a lane on") {
        it("is the first item of the first group") {
            val listings = listOf(listing("a", RequestState.Ready, id = 1), listing("b", RequestState.OnItsWay, id = 2))

            mainRequest(listings, CHOOSER)!!.request.id shouldBe 2
        }

        it("is none for a book with no requests") {
            mainRequest(emptyList(), CHOOSER) shouldBe null
        }
    }
})
