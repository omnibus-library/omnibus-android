package com.omnibus.omnibus.feature.requests

import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.CoverOffer
import com.omnibus.omnibus.data.requests.ReleaseProtocol
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestVersion
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.StuckReason
import com.omnibus.omnibus.data.requests.Wanter
import com.omnibus.omnibus.data.requests.randomBookRequest
import com.omnibus.omnibus.data.requests.randomRequestListing
import com.omnibus.omnibus.data.requests.randomRequestVersion
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContainIgnoringCase
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import java.util.TimeZone

private const val NBSP = " "
private const val DOT = "$NBSP· "

/** A request with only what a rule reads pinned. The rest is random, so a rule can't lean on it. */
private fun request(
    state: RequestState = RequestState.Looking,
    progress: Double? = null,
    kind: RequestKind = RequestKind.Ebook,
    versions: List<RequestVersion> = emptyList(),
): BookRequest =
    randomBookRequest().copy(state = state, progress = progress, kind = kind, versions = versions, cancelAt = null, chosenResultId = null)

private fun version(
    id: Long = randomLong(),
    format: String? = "EPUB",
    sizeBytes: Long? = 1_800_000,
    indexer: String? = "MyAnonamouse",
    seeders: Long? = 142,
    protocol: ReleaseProtocol? = ReleaseProtocol.Torrent,
    bestMatch: Boolean = false,
    rejected: Boolean = false,
) = randomRequestVersion().copy(
    id = id, format = format, sizeBytes = sizeBytes, indexer = indexer, seeders = seeders, protocol = protocol,
    bestMatch = bestMatch, rejected = rejected,
)

private val ASKER = RequestViewer(id = 1, canStart = true, canChoose = false)
private val CHOOSER = RequestViewer(id = 2, canStart = true, canChoose = true)

class RequestRulesTest : DescribeSpec({

    describe("a request's state in words") {
        it("heads each state") {
            checkAll(
                Exhaustive.of(
                    RequestState.Looking to "Looking",
                    RequestState.Choose to "Choose a version",
                    RequestState.Chosen to "Version chosen",
                    RequestState.OnItsWay to "On its way",
                    RequestState.Stuck to "Couldn’t find it",
                    RequestState.Cancelled to "Cancelled",
                ),
            ) { (state, heading) -> stateView(state).heading shouldBe heading }
        }

        it("gives choose no note, since the versions found carry it") {
            stateView(RequestState.Choose).note shouldBe null
        }

        it("names ready by its kind") {
            stateView(RequestState.Ready, kind = RequestKind.Ebook).heading shouldBe "Ready to read"
            stateView(RequestState.Ready, kind = RequestKind.Audiobook).heading shouldBe "Ready to listen"
        }

        context("when the request is stuck") {
            it("falls back to a general line for each reason with no error recorded") {
                checkAll(
                    Exhaustive.of(
                        StuckReason.NotFound to ("Couldn’t find it" to "Nothing worth showing turned up."),
                        StuckReason.SearchFailed to ("Couldn’t find it" to "Nothing worth showing turned up."),
                        StuckReason.StoppedMoving to ("Stopped moving" to "The download stopped making progress."),
                        StuckReason.CouldntAdd to ("Couldn’t add it" to "It downloaded but couldn’t be added to the library."),
                    ),
                ) { (reason, view) ->
                    stateView(RequestState.Stuck, reason).let { it.heading to it.note } shouldBe view
                }
            }

            it("puts the recorded error under the heading, its first letter capitalised") {
                stateView(RequestState.Stuck, StuckReason.StoppedMoving, stuckDetail = "the download made no progress").note shouldBe
                    "The download made no progress"
            }

            it("leaves a name that opens with a lower-case letter and a capital as written") {
                stateView(RequestState.Stuck, StuckReason.CouldntAdd, stuckDetail = "qBittorrent did not accept it").note shouldBe
                    "qBittorrent did not accept it"
            }

            it("ignores a blank recorded error") {
                stateView(RequestState.Stuck, StuckReason.StoppedMoving, stuckDetail = "  ").note shouldBe
                    "The download stopped making progress."
            }
        }

        it("never uses the downloader's words") {
            val words = listOf("release", "torrent", "indexer", "grab", "seed")
            val wording = RequestWording()
            val views = RequestState.entries.map { stateView(it) } +
                StuckReason.entries.map { stateView(RequestState.Stuck, it) } +
                RequestStateView(wording.lookingLabel, wording.lookingSlowLabel)
            views.forEach { view ->
                words.forEach { view.heading shouldNotContainIgnoringCase it }
                words.forEach { (view.note ?: "") shouldNotContainIgnoringCase it }
            }
        }
    }

    describe("a download's progress") {
        it("reads 0.29 as 29 through floating-point noise") {
            progressPercent(0.29) shouldBe 29
        }

        it("rounds down, and holds under 100 until the download is done") {
            listOf(progressPercent(0.426), progressPercent(0.999), progressPercent(0.99996)) shouldBe listOf(42, 99, 99)
        }

        it("reads a finished download as 100 and an unread one as 0") {
            listOf(progressPercent(1.0), progressPercent(null)) shouldBe listOf(100, 0)
        }

        it("shows a percent only while a download runs") {
            checkAll(
                Exhaustive.of(
                    request(RequestState.OnItsWay, 0.62) to 62,
                    request(RequestState.OnItsWay, null) to null,
                    request(RequestState.OnItsWay, 1.0) to null,
                    request(RequestState.Stuck, 0.4) to null,
                ),
            ) { (request, percent) -> downloadPercent(request) shouldBe percent }
        }

        it("reads a request as downloading until its progress reaches 1") {
            checkAll(
                Exhaustive.of(
                    request(RequestState.OnItsWay, null) to true,
                    request(RequestState.OnItsWay, 0.99) to true,
                    request(RequestState.OnItsWay, 1.0) to false,
                    request(RequestState.Chosen, null) to false,
                    request(RequestState.Stuck, 0.4) to false,
                ),
            ) { (request, downloading) -> isDownloading(request) shouldBe downloading }
        }
    }

    describe("which requests are moving, live and cancellable") {
        it("is moving while searching, picked or downloading") {
            RequestState.entries.filter { isMoving(it) } shouldBe
                listOf(RequestState.Looking, RequestState.Chosen, RequestState.OnItsWay)
        }

        it("is live while moving, and while a pick is still to make") {
            RequestState.entries.filter { isLive(it) } shouldBe
                listOf(RequestState.Looking, RequestState.Choose, RequestState.Chosen, RequestState.OnItsWay)
        }

        it("offers Cancel until a download is complete") {
            checkAll(
                Exhaustive.of(
                    request(RequestState.Looking) to true,
                    request(RequestState.Choose) to true,
                    request(RequestState.Chosen) to true,
                    request(RequestState.OnItsWay, 0.4) to true,
                    request(RequestState.OnItsWay, 1.0) to false,
                    request(RequestState.Ready, 1.0) to false,
                    request(RequestState.Stuck) to false,
                    request(RequestState.Cancelled) to false,
                ),
            ) { (request, cancellable) -> isCancellable(request) shouldBe cancellable }
        }
    }

    describe("who may cancel") {
        it("lets a chooser cancel any request, and an asker only their own") {
            val mine = request().copy(userId = ASKER.id)
            val theirs = request().copy(userId = 99)

            listOf(canCancel(CHOOSER, theirs), canCancel(ASKER, mine), canCancel(ASKER, theirs)) shouldBe
                listOf(true, true, false)
        }
    }

    describe("the stepper") {
        it("makes Looking the current step of a search") {
            stepsFor(request(RequestState.Looking))!!.map { it.label to it.status } shouldBe listOf(
                "Looking" to StepStatus.Current,
                "Choose a version" to StepStatus.Todo,
                "On its way" to StepStatus.Todo,
                "Ready" to StepStatus.Todo,
            )
        }

        it("counts what the search found once the reader is choosing") {
            val steps = stepsFor(request(RequestState.Choose, versions = listOf(version(), version())))!!

            steps.map { it.label to it.status }.take(2) shouldBe
                listOf("Looking · 2 found" to StepStatus.Done, "Choose a version" to StepStatus.Current)
        }

        it("names the pick made and carries the download's percent") {
            val steps = stepsFor(request(RequestState.OnItsWay, 0.62, versions = listOf(version())))!!

            steps.drop(1).take(2) shouldBe listOf(
                RequestStep("Chose a version", StepStatus.Done, null),
                RequestStep("On its way", StepStatus.Current, 62),
            )
        }

        it("marks every step done once the book is ready") {
            stepsFor(request(RequestState.Ready, 1.0))!!.map { it.status }.toSet() shouldBe setOf(StepStatus.Done)
        }

        it("has no steps for a stuck or cancelled request") {
            listOf(stepsFor(request(RequestState.Stuck)), stepsFor(request(RequestState.Cancelled))) shouldBe listOf(null, null)
        }
    }

    describe("the time a download has left") {
        val now = 1_000_000L
        fun left(secs: Long, progress: Double = 0.5) =
            timeLeft(request(RequestState.OnItsWay, progress).copy(expectedDoneAt = now / 1000 + secs), now)

        it("reads the seconds to go in the web's words") {
            checkAll(
                Exhaustive.of(
                    1L to "less than a minute left",
                    59L to "less than a minute left",
                    60L to "about 1 min left",
                    150L to "about 3 min left",
                    3599L to "about 1 hour left",
                    7200L to "about 2 hours left",
                ),
            ) { (secs, words) -> left(secs) shouldBe words }
        }

        it("says nothing when the time has come or passed") {
            listOf(left(0), left(-30)) shouldBe listOf(null, null)
        }

        it("says nothing when the client can't tell, or the download is complete") {
            listOf(timeLeft(request(progress = 0.5).copy(expectedDoneAt = null), now), left(120, progress = 1.0)) shouldBe
                listOf(null, null)
        }
    }

    describe("how long ago") {
        it("reads the seconds since in the web's words") {
            val now = 10_000_000L * 1000
            checkAll(
                Exhaustive.of(
                    0L to "just now",
                    89L to "just now",
                    90L to "1m ago",
                    3599L to "59m ago",
                    3600L to "1h ago",
                    86_399L to "23h ago",
                    86_400L to "yesterday",
                    172_799L to "yesterday",
                    172_800L to "2d ago",
                ),
            ) { (ago, words) -> recency(10_000_000L - ago, now) shouldBe words }
        }

        it("reads the future as just now") {
            recency(10_000_100L, 10_000_000L * 1000) shouldBe "just now"
        }

        it("names the asker as You, or by the requester's name") {
            val listing = randomRequestListing().let { it.copy(request = it.request.copy(userId = 5, createdAt = 100), requesterName = "Jordan") }

            listOf(askedBy(listing, 5, 100_000), askedBy(listing, 6, 100_000)) shouldBe
                listOf("You$DOT" + "just now", "Jordan$DOT" + "just now")
        }
    }

    describe("a version's lines") {
        it("writes a size in decimal units with a non-breaking space") {
            checkAll(
                Exhaustive.of(
                    2_400_000L to "2.4${NBSP}MB",
                    1_500_000_000L to "1.5${NBSP}GB",
                    1_500L to "1.5${NBSP}KB",
                    999L to "999${NBSP}B",
                    0L to null,
                    null to null,
                ),
            ) { (bytes, size) -> versionSize(bytes) shouldBe size }
        }

        it("names the source, format, size and seeds") {
            versionSource(version()) shouldBe "MyAnonamouse${DOT}EPUB${DOT}1.8${NBSP}MB${DOT}142${NBSP}seeds"
        }

        it("drops a missing source and missing seeds") {
            versionSource(version(indexer = null, seeders = null)) shouldBe "EPUB${DOT}1.8${NBSP}MB"
        }

        it("ends a Usenet version's line with Usenet in place of seeds") {
            versionSource(version(indexer = "NZBgeek", seeders = null, protocol = ReleaseProtocol.Usenet)) shouldBe
                "NZBgeek${DOT}EPUB${DOT}1.8${NBSP}MB${DOT}Usenet"
        }

        it("says when the format and size are unknown") {
            versionSource(version(format = null, sizeBytes = null, seeders = null)) shouldBe
                "MyAnonamouse${DOT}Format unknown${DOT}Size unknown"
        }

        it("writes the seed count with thousands separators and a singular") {
            checkAll(Exhaustive.of(1L to "1${NBSP}seed", 0L to "0${NBSP}seeds", 1234L to "1,234${NBSP}seeds")) { (seeders, seeds) ->
                versionSource(version(indexer = null, seeders = seeders)) shouldBe "EPUB${DOT}1.8${NBSP}MB$DOT$seeds"
            }
        }

        it("converts only AZW3 and MOBI to EPUB") {
            checkAll(Exhaustive.of("AZW3" to true, "MOBI" to true, "EPUB" to false, "PDF" to false, null to false)) { (format, converts) ->
                convertsToEpub(version(format = format)) shouldBe converts
            }
        }

        it("names a picked version's size and source, and its format and size, each left out when unknown") {
            listOf(
                pickedMeta(version()),
                pickedMeta(version(sizeBytes = null, indexer = null)),
                pickedHeading(version()),
                pickedHeading(version(format = null, sizeBytes = null)),
            ) shouldBe listOf("1.8${NBSP}MB${DOT}MyAnonamouse", "", "EPUB$DOT" + "1.8${NBSP}MB", "")
        }
    }

    describe("the versions found") {
        it("counts only the versions that can be chosen, naming the best match when one of them is") {
            versionsFound(listOf(version(bestMatch = true), version(), version(rejected = true))) shouldBe
                "2 versions found${DOT}Best match first"
        }

        it("names no best match when none that can be chosen is one") {
            versionsFound(listOf(version(), version(bestMatch = true, rejected = true))) shouldBe "1 version found"
        }

        it("shows the first four that can be chosen and every tried one") {
            val versions = (1L..6L).map { version(id = it) } + version(id = 7, rejected = true)

            firstShown(versions).map { it.id } shouldBe listOf(1L, 2, 3, 4, 7)
        }

        it("shows every version when exactly four can be chosen") {
            val versions = (1L..4L).map { version(id = it) } + version(id = 5, rejected = true)

            firstShown(versions).map { it.id } shouldBe listOf(1L, 2, 3, 4, 5)
        }
    }

    describe("the cover offer's sizes") {
        it("names both sizes without saying where the existing cover came from") {
            coverSizesLine(CoverOffer(600, 900, 128, 196)) shouldBe
                "Its cover is 600${NBSP}×${NBSP}900. The existing one is 128${NBSP}×${NBSP}196."
        }
    }

    describe("why a failure happened") {
        it("explains a failed start by its status, and plainly for any other failure") {
            checkAll(
                Exhaustive.of(
                    RequestsError.Http(429, null) to "You have a lot of requests going. Try again in a few minutes.",
                    RequestsError.Http(403, "x") to "Your account can’t make requests.",
                    RequestsError.Http(404, null) to "That book is no longer in the library.",
                    RequestsError.Http(500, "boom") to "Couldn’t start the request.",
                    RequestsError.Unreachable to "Couldn’t start the request.",
                ),
            ) { (error, words) -> startFailure(error) shouldBe words }
        }

        it("names the action, then the server's message") {
            actionFailure(RequestAction.Cancel, RequestsError.Http(409, "Too late")) shouldBe "Couldn’t cancel: Too late"
        }

        it("names each action's own prefix") {
            RequestAction.entries.map { actionFailure(it, RequestsError.Http(400, "m")).substringBefore(":") } shouldBe listOf(
                "Couldn’t choose that version", "Couldn’t cancel", "Couldn’t undo", "Couldn’t choose another",
                "Couldn’t try again", "Couldn’t use that cover", "Couldn’t keep this one",
            )
        }

        it("says something went wrong for a failure that is not the server's") {
            checkAll(Exhaustive.of(RequestsError.Unreachable, RequestsError.SessionExpired, RequestsError.UnexpectedResponse)) {
                actionFailure(RequestAction.Undo, it) shouldBe "Couldn’t undo: Something went wrong."
            }
        }

        it("names the status when the server sent no message") {
            actionFailure(RequestAction.Retry, RequestsError.Http(503, null)) shouldBe "Couldn’t try again: Server error (503)."
        }
    }

    describe("the Undo window") {
        it("ends 1.5 seconds before the server stops taking the cancel back") {
            undoDelayMillis(110, 100_000) shouldBe 8_500
        }

        it("never goes negative once the grace has all but gone") {
            listOf(undoDelayMillis(101, 100_000), undoDelayMillis(90, 100_000), undoDelayMillis(101, 99_500)) shouldBe listOf(0L, 0L, 0L)
        }

        it("offers no Undo without a cancel_at") {
            undoDelayMillis(null, 100_000) shouldBe null
        }
    }

    describe("offering Request again") {
        val now = 1_000_000L
        val cancelled = request(RequestState.Cancelled, kind = RequestKind.Ebook)

        it("offers it on a cancelled request once its undo has run out") {
            offersRequestAgain(cancelled, ASKER, emptyList(), now) shouldBe true
        }

        it("holds it back while the undo is open, and offers it the moment it ends") {
            listOf(
                offersRequestAgain(cancelled.copy(cancelAt = now / 1000 + 1), ASKER, emptyList(), now),
                offersRequestAgain(cancelled.copy(cancelAt = now / 1000), ASKER, emptyList(), now),
            ) shouldBe listOf(false, true)
        }

        it("holds it back while the viewer has that kind going for the book") {
            val live = request(RequestState.OnItsWay, kind = RequestKind.Ebook).copy(userId = ASKER.id)

            offersRequestAgain(cancelled, ASKER, listOf(live), now) shouldBe false
        }

        it("holds it back while anyone has that kind finished for the book, which the library now holds") {
            val mine = request(RequestState.Ready, kind = RequestKind.Ebook).copy(userId = ASKER.id)
            val theirs = request(RequestState.Ready, kind = RequestKind.Ebook).copy(userId = 99)

            listOf(offersRequestAgain(cancelled, ASKER, listOf(mine), now), offersRequestAgain(cancelled, ASKER, listOf(theirs), now)) shouldBe
                listOf(false, false)
        }

        it("offers it beside another reader's live request, or the viewer's live request of the other kind") {
            val theirs = request(RequestState.Looking, kind = RequestKind.Ebook).copy(userId = 99)
            val audio = request(RequestState.Looking, kind = RequestKind.Audiobook).copy(userId = ASKER.id)

            listOf(offersRequestAgain(cancelled, ASKER, listOf(theirs), now), offersRequestAgain(cancelled, ASKER, listOf(audio), now)) shouldBe
                listOf(true, true)
        }

        it("holds it back from a reader who can't start requests") {
            offersRequestAgain(cancelled, ASKER.copy(canStart = false), emptyList(), now) shouldBe false
        }

        it("holds it back on a request that wasn't cancelled") {
            offersRequestAgain(request(RequestState.Stuck), ASKER, emptyList(), now) shouldBe false
        }
    }

    describe("a search that runs long") {
        it("turns slow at twenty seconds and not a second before") {
            listOf(isSearchSlow(1000, 1_019_000), isSearchSlow(1000, 1_020_000)) shouldBe listOf(false, true)
        }

        it("turns stale at a hundred and fifty seconds and not a second before") {
            listOf(isSearchStale(1000, 1_149_000), isSearchStale(1000, 1_150_000)) shouldBe listOf(false, true)
        }

        it("labels the search by how long it has run") {
            val search = request(RequestState.Looking).copy(updatedAt = 1000)

            listOf(lookingLabel(search, 1_019_000), lookingLabel(search, 1_020_000)) shouldBe
                listOf("Looking for versions…", "Still searching for versions…")
        }

        it("offers Try again on a stale search to a chooser only") {
            val search = request(RequestState.Looking).copy(updatedAt = 1000)

            listOf(offersLookingRetry(search, CHOOSER, 1_150_000), offersLookingRetry(search, ASKER, 1_150_000), offersLookingRetry(search, CHOOSER, 1_149_000)) shouldBe
                listOf(true, false, false)
        }
    }

    describe("the kinds a viewer may request") {
        it("lists the kinds the library lacks that no request covers") {
            val covered = randomRequestListing().let { it.copy(request = it.request.copy(kind = RequestKind.Audiobook)) }
            fun finished(kind: RequestKind) = randomRequestListing().let { it.copy(request = it.request.copy(kind = kind, state = RequestState.Ready)) }

            listOf(
                unrequestedKinds(listOf("PRINT"), emptyList(), ASKER),
                unrequestedKinds(listOf("EPUB"), emptyList(), ASKER),
                unrequestedKinds(listOf("PRINT"), listOf(covered), ASKER),
                unrequestedKinds(listOf("PRINT"), emptyList(), ASKER.copy(canStart = false)),
                unrequestedKinds(listOf("EPUB", "M4B"), emptyList(), ASKER),
                unrequestedKinds(listOf("PRINT"), listOf(finished(RequestKind.Ebook), finished(RequestKind.Audiobook)), ASKER),
            ) shouldBe listOf(
                listOf(RequestKind.Ebook, RequestKind.Audiobook),
                listOf(RequestKind.Audiobook),
                listOf(RequestKind.Ebook),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }

    describe("whether Cancel is offered") {
        it("needs both the right to cancel and a request that can still be cancelled") {
            val mine = request(RequestState.Looking).copy(userId = ASKER.id)
            val theirs = request(RequestState.Looking).copy(userId = 99)
            val finished = request(RequestState.Ready).copy(userId = ASKER.id)

            listOf(offersCancel(ASKER, mine), offersCancel(ASKER, theirs), offersCancel(CHOOSER, theirs), offersCancel(ASKER, finished)) shouldBe
                listOf(true, false, true, false)
        }
    }

    describe("when the page reads again") {
        it("polls every two seconds while any lane is moving, and not at all otherwise") {
            listOf(
                detailPollIntervalMillis(listOf(request(RequestState.Ready), request(RequestState.Chosen))),
                detailPollIntervalMillis(listOf(request(RequestState.Choose), request(RequestState.Stuck))),
                detailPollIntervalMillis(emptyList()),
                detailPollIntervalMillis(listOf(request(RequestState.Ready), request(RequestState.Ready))),
            ) shouldBe listOf(2_000L, null, null, null)
        }
    }

    describe("splitting a book's requests into lanes and history") {
        fun listing(request: BookRequest) = randomRequestListing().let { it.copy(request = request) }
        val shown = 7L

        it("gives another reader's live request, and a stuck one, a lane of its own") {
            val live = listing(request(RequestState.Choose).copy(id = 8))
            val stuck = listing(request(RequestState.Stuck).copy(id = 4))

            splitBookRequests(listOf(live, stuck), shown).let { it.lanes to it.history } shouldBe (listOf(live, stuck) to emptyList())
        }

        it("keeps a cancelled request in the history only") {
            val gone = listing(request(RequestState.Cancelled).copy(id = 5))

            splitBookRequests(listOf(gone), shown).let { it.lanes to it.history } shouldBe (emptyList<Any>() to listOf(gone))
        }

        it("keeps a request that swapped a version in the history, unless it is stuck") {
            val swapped = listing(request(RequestState.Ready, versions = listOf(version(), version(rejected = true))).copy(id = 3))
            val stuckSwapped = listing(request(RequestState.Stuck, versions = listOf(version(rejected = true))).copy(id = 2))

            splitBookRequests(listOf(swapped, stuckSwapped), shown).history shouldBe listOf(swapped)
        }

        it("gives a finished request a lane of its own, in the history too when it swapped a version") {
            val ready = listing(request(RequestState.Ready, versions = listOf(version())).copy(id = 3))
            val readySwapped = listing(request(RequestState.Ready, versions = listOf(version(), version(rejected = true))).copy(id = 2))

            splitBookRequests(listOf(ready, readySwapped), shown).let { it.lanes to it.history } shouldBe
                (listOf(ready, readySwapped) to listOf(readySwapped))
        }

        it("never lists the request on screen") {
            val onScreen = listing(request(RequestState.Cancelled).copy(id = shown))

            splitBookRequests(listOf(onScreen), shown).let { it.lanes to it.history } shouldBe (emptyList<Any>() to emptyList())
        }
    }

    describe("the history") {
        val utc = TimeZone.getTimeZone("UTC")
        val lateOnOct12Utc = 1_791_846_000L // 2026-10-12T23:00:00Z

        it("names the day on the given calendar, without the year") {
            listOf(historyDay(lateOnOct12Utc, utc), historyDay(lateOnOct12Utc, TimeZone.getTimeZone("Pacific/Kiritimati"))) shouldBe
                listOf("Oct 12", "Oct 13")
        }

        it("says who cancelled, as you, by name, or nobody") {
            fun line(by: Long?, name: String?) = historyLine(
                randomRequestListing().let { it.copy(request = it.request.copy(state = RequestState.Cancelled, cancelledBy = by, kind = RequestKind.Ebook, updatedAt = lateOnOct12Utc), cancelledByName = name) },
                viewerId = 1, zone = utc,
            )

            listOf(line(1, "Ada"), line(2, "Jordan"), line(null, null)) shouldBe listOf(
                "Ebook cancelled by you$DOT" + "Oct${NBSP}12",
                "Ebook cancelled by Jordan$DOT" + "Oct${NBSP}12",
                "Ebook cancelled$DOT" + "Oct${NBSP}12",
            )
        }

        it("heads a ready audiobook by its kind") {
            val ready = randomRequestListing().let { it.copy(request = it.request.copy(state = RequestState.Ready, kind = RequestKind.Audiobook, updatedAt = lateOnOct12Utc)) }

            historyLine(ready, 1, utc) shouldBe "Audiobook ready to listen$DOT" + "Oct${NBSP}12"
        }

        it("names the version and how far it got, only for an ended request") {
            val picked = version(id = 4, format = "EPUB", sizeBytes = 2_400_000)
            fun details(state: RequestState, progress: Double?) = historyDetails(
                randomRequestListing().let { it.copy(request = it.request.copy(state = state, progress = progress, chosenResultId = 4, versions = listOf(picked))) },
            )

            listOf(details(RequestState.Cancelled, 0.4), details(RequestState.Ready, 0.4), details(RequestState.Cancelled, 1.0)) shouldBe listOf(
                "EPUB$DOT" + "2.4${NBSP}MB$DOT" + "stopped at 40%",
                "EPUB$DOT" + "2.4${NBSP}MB",
                "EPUB$DOT" + "2.4${NBSP}MB",
            )
        }

        it("has no details when no version was picked and the download never started") {
            historyDetails(randomRequestListing().let { it.copy(request = it.request.copy(chosenResultId = null, progress = null, versions = emptyList())) }) shouldBe null
        }

        it("lists one line per rejected version") {
            val listing = randomRequestListing().let { it.copy(request = it.request.copy(versions = listOf(version(id = 1), version(id = 2, format = null, sizeBytes = 9_100_000, rejected = true)))) }

            swapFacts(listing) shouldBe listOf(SwapFact(2, "Swapped for another version · Unknown · 9.1${NBSP}MB"))
        }
    }

    describe("who wants the book") {
        fun line(vararg wanters: Wanter) = wishersLine(wanters.toList(), viewerId = 1)

        it("reads each crowd in the web's words") {
            listOf(
                line(),
                line(Wanter(2, "Jordan")),
                line(Wanter(2, "Jordan"), Wanter(3, "Riley")),
                line(Wanter(2, "Jordan"), Wanter(1, "Ada"), Wanter(3, "Riley")),
                line(Wanter(1, "Ada")),
            ) shouldBe listOf(
                null,
                "On Jordan’s wishlist",
                "On Jordan’s and Riley’s wishlists",
                "On Jordan’s, your and Riley’s wishlists",
                "On your wishlist",
            )
        }
    }

    describe("a request's kind") {
        it("is labelled and chipped by its format") {
            listOf(kindLabel(RequestKind.Ebook), kindLabel(RequestKind.Audiobook), kindChip(RequestKind.Ebook), kindChip(RequestKind.Audiobook)) shouldBe
                listOf("Ebook", "Audiobook", "EPUB", "M4B")
        }
    }
})
