package com.omnibus.omnibus.feature.requests.page

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.NobodyViewer
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.TestRequestsRepository
import com.omnibus.omnibus.data.requests.Wanter
import com.omnibus.omnibus.data.requests.randomBookRequest
import com.omnibus.omnibus.data.requests.randomRequestListing
import com.omnibus.omnibus.data.requests.randomRequestVersion
import com.omnibus.omnibus.data.requests.randomWanter
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val CHOOSER = RequestViewer(id = 1, canStart = true, canChoose = true)
private val ASKER = RequestViewer(id = 1, canStart = true, canChoose = false)
private val BLOCKED = RequestViewer(id = 1, canStart = false, canChoose = false)

private fun authAs(viewer: RequestViewer) = TestAuthRepository().apply {
    currentUserState.value = randomCurrentUser().copy(
        id = viewer.id,
        bookRequests = true,
        canStartRequests = viewer.canStart,
        canChooseRequestVersions = viewer.canChoose,
    )
}

/** A request for the book [uuid]. Only what the page reads is pinned. */
private fun request(
    uuid: String,
    id: Long,
    state: RequestState = RequestState.Looking,
    kind: RequestKind = RequestKind.Ebook,
    userId: Long = 1,
    updatedAt: Long = 100,
): BookRequest = randomBookRequest().copy(
    id = id, bookUuid = uuid, state = state, kind = kind, userId = userId, updatedAt = updatedAt,
    progress = null, expectedDoneAt = null, cancelAt = null, chosenResultId = null, versions = emptyList(),
)

private fun listingOf(request: BookRequest): RequestListing =
    randomRequestListing().let { it.copy(request = request, book = it.book.copy(uniqueIdentifier = request.bookUuid)) }

private fun ok(request: BookRequest) = RequestsResult.Success(request)

private val UNREACHABLE = RequestsResult.Failure(RequestsError.Unreachable)

/** A server that answers this book's page: its listings, then each request's full read. */
private fun TestRequestsRepository.serve(uuid: String, vararg requests: BookRequest) {
    answerAlways("bookList", RequestsResult.Success(requests.map(::listingOf)), uuid)
    requests.forEach { answerAlways("request", ok(it), it.id) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookRequestViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        uuid: String = randomString(),
        requests: TestRequestsRepository = TestRequestsRepository(),
        library: TestLibraryRepository = TestLibraryRepository(),
        auth: TestAuthRepository = TestAuthRepository(),
        time: TestTimeSource = TestTimeSource(),
        online: Boolean = true,
    ) = BookRequestViewModel(uuid, requests, library, auth, time, TestConnectivityMonitor(online)) { RequestWording() }

    /** A library that has the book [uuid]. */
    fun libraryWith(uuid: String) = TestLibraryRepository().apply {
        bookResult = BookResult.Success(randomBook().copy(uniqueIdentifier = uuid, formats = listOf("PRINT")))
    }

    describe("loading the page") {

        it("reads the book, its listings and its wishers, then each lane's full request") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            val main = request(uuid, id = 5, state = RequestState.OnItsWay)
            val other = request(uuid, id = 6, state = RequestState.Choose, userId = 2)
            requests.serve(uuid, main, other)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val library = libraryWith(uuid)

            sut(uuid, requests, library, authAs(CHOOSER)).load()

            listOf(library.bookRequests, requests.callsTo("bookList").size, requests.callsTo("wanters").size) shouldBe
                listOf(listOf(uuid), 1, 1)
            requests.callsTo("request").map { it.args.single() }.toSet() shouldBe setOf(5L, 6L)
        }

        it("lays out the lane the page opened on, then each other lane, and the history") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            val main = request(uuid, id = 5, state = RequestState.OnItsWay)
            val other = request(uuid, id = 6, state = RequestState.Looking, userId = 2)
            val gone = request(uuid, id = 4, state = RequestState.Cancelled)
            requests.serve(uuid, other, main, gone)
            requests.answer("wanters", RequestsResult.Success(listOf(randomWanter())))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

            vm.load()

            vm.uiState.value.let { state ->
                Triple(state.lanes.map { it.id }, state.history.map { it.request.id }, state.wanters?.size)
            } shouldBe Triple(listOf(5L, 6L), listOf(4L), 1)
        }

        it("shows a finished ebook and a finished audiobook as lanes again when the page is entered anew") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.serve(
                uuid,
                request(uuid, 5, RequestState.Ready, kind = RequestKind.Ebook),
                request(uuid, 6, RequestState.Ready, kind = RequestKind.Audiobook),
            )
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val library = libraryWith(uuid)
            val vm = sut(uuid, requests, library, authAs(CHOOSER))

            vm.load()

            vm.uiState.value.let { it.lanes.map { lane -> lane.kind }.toSet() to it.pollInterval } shouldBe
                (setOf(RequestKind.Ebook, RequestKind.Audiobook) to null)
        }

        it("leaves the wishers line out when that read fails") {
            val uuid = randomString()
            val requests = TestRequestsRepository().apply { serve(uuid, request(uuid, 5)) }
            requests.answer("wanters", UNREACHABLE)
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

            vm.load()

            vm.uiState.value.let { it.wanters to it.failure } shouldBe (null to null)
        }

        it("shows the book as not found when the library has no such book") {
            val uuid = randomString()
            val requests = TestRequestsRepository().apply { serve(uuid) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, TestLibraryRepository().apply { bookResult = BookResult.NotFound }, authAs(CHOOSER))

            vm.load()

            vm.uiState.value.let { it.isMissing to it.book } shouldBe (true to null)
        }

        it("loads a book with no requests with no lanes") {
            val uuid = randomString()
            val requests = TestRequestsRepository().apply { serve(uuid) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

            vm.load()

            vm.uiState.value.let { Triple(it.book != null, it.lanes, it.failure) } shouldBe Triple(true, emptyList(), null)
        }

        context("when the first load fails") {
            it("shows the error when the listings read fails") {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                requests.answerAlways("bookList", RequestsResult.Failure(RequestsError.Http(500, "boom")), uuid)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

                vm.load()

                vm.uiState.value.failure shouldBe RequestsError.Http(500, "boom")
            }

            it("shows the error when the book read fails") {
                val uuid = randomString()
                val requests = TestRequestsRepository().apply { serve(uuid, request(uuid, 5)) }
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val library = TestLibraryRepository().apply { bookResult = BookResult.Failure(LibraryError.Unreachable) }
                val vm = sut(uuid, requests, library, authAs(CHOOSER))

                vm.load()

                vm.uiState.value.failure shouldBe RequestsError.Unreachable
            }

            it("shows the error when the main lane's read fails") {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                requests.answerAlways("bookList", RequestsResult.Success(listOf(listingOf(request(uuid, 5)))), uuid)
                requests.answerAlways("request", RequestsResult.Failure(RequestsError.UnexpectedResponse), 5L)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

                vm.load()

                vm.uiState.value.failure shouldBe RequestsError.UnexpectedResponse
            }

            it("loads the page when the retry succeeds") {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                requests.answer("bookList", UNREACHABLE)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))
                vm.load()
                requests.serve(uuid, request(uuid, 5))
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))

                vm.load()

                vm.uiState.value.let { it.failure to it.lanes.map { lane -> lane.id } } shouldBe (null to listOf(5L))
            }
        }

        context("when a later load fails") {
            it("keeps the last good page") {
                val uuid = randomString()
                val requests = TestRequestsRepository().apply { serve(uuid, request(uuid, 5)) }
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))
                vm.load()
                val good = vm.uiState.value.copy(nowMillis = 0)
                requests.answer("bookList", UNREACHABLE)
                requests.answer("wanters", UNREACHABLE)

                vm.load()

                vm.uiState.value.copy(nowMillis = 0, wanters = good.wanters) shouldBe good
            }
        }
    }

    describe("a session confirmed after the page opened") {

        it("takes the user's permissions when they arrive") {
            val auth = TestAuthRepository()
            val vm = sut(auth = auth)

            auth.currentUserState.value = randomCurrentUser().copy(id = 1, bookRequests = true, canStartRequests = true, canChooseRequestVersions = true)

            vm.uiState.value.viewer shouldBe CHOOSER
        }

        it("shows the lanes read-only while nobody is known") {
            val uuid = randomString()
            val requests = TestRequestsRepository().apply { serve(uuid, request(uuid, 5, RequestState.Choose)) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), TestAuthRepository())

            vm.load()

            vm.uiState.value.let { it.lanes.map { lane -> lane.id } to it.viewer } shouldBe (listOf(5L) to NobodyViewer)
        }

        it("picks the lane to open on again, and reads the lanes again, once someone is known") {
            val uuid = randomString()
            val looking = request(uuid, 5, RequestState.Looking)
            val choosing = request(uuid, 6, RequestState.Choose, userId = 2)
            val requests = TestRequestsRepository().apply { serve(uuid, looking, choosing) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val auth = TestAuthRepository()
            val vm = sut(uuid, requests, libraryWith(uuid), auth)
            vm.load()
            val before = vm.uiState.value.lanes.map { it.id }

            auth.currentUserState.value = randomCurrentUser().copy(id = 1, bookRequests = true, canStartRequests = true, canChooseRequestVersions = true)

            Triple(before, vm.uiState.value.lanes.map { it.id }, requests.callsTo("bookList").size) shouldBe
                Triple(listOf(5L, 6L), listOf(6L, 5L), 2)
        }

        it("may do nothing when the server has book requests off for the reader") {
            val auth = TestAuthRepository().apply {
                currentUserState.value = randomCurrentUser().copy(id = 1, bookRequests = false, canStartRequests = true, canChooseRequestVersions = true)
            }

            sut(auth = auth).uiState.value.viewer shouldBe RequestViewer(1, canStart = false, canChoose = false)
        }
    }

    describe("the lane the page opened on") {

        it("stays the main lane when a reload brings a more urgent request") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            val first = request(uuid, id = 5, state = RequestState.Looking)
            requests.serve(uuid, first)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))
            vm.load()
            val urgent = request(uuid, id = 6, state = RequestState.Choose, userId = 2)
            requests.serve(uuid, urgent, first)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))

            vm.load()

            vm.uiState.value.let { it.mainId to it.lanes.map { lane -> lane.id } } shouldBe (5L to listOf(5L, 6L))
        }

        it("is the most urgent request when none was set yet") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.serve(uuid, request(uuid, id = 5, state = RequestState.Looking), request(uuid, id = 6, state = RequestState.Choose))
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

            vm.load()

            vm.uiState.value.mainId shouldBe 6
        }

        it("is chosen again when the request it was set to is no longer listed") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.serve(uuid, request(uuid, id = 5, state = RequestState.Looking))
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))
            vm.load()
            requests.serve(uuid, request(uuid, id = 9, state = RequestState.Looking))
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))

            vm.load()

            vm.uiState.value.mainId shouldBe 9
        }
    }

    describe("a lane whose own read fails") {

        it("stays on the page, with its last good read, when a reload can't read it") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            val main = request(uuid, 5, RequestState.Choose)
            val other = request(uuid, 6, RequestState.Choose, userId = 2)
            requests.serve(uuid, main, other)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))
            vm.load()
            requests.answer("request", UNREACHABLE, forFirstArg = 6L)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))

            vm.load()

            vm.uiState.value.lanes.map { it.id } shouldBe listOf(5L, 6L)
        }

        it("is read again by the poll, even when nothing is moving") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val main = request(uuid, 5, RequestState.Choose)
                val other = request(uuid, 6, RequestState.Choose, userId = 2)
                requests.serve(uuid, main, other)
                requests.answer("request", UNREACHABLE, forFirstArg = 6L)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))
                vm.load()
                val before = vm.uiState.value.lanes.map { it.id }
                backgroundScope.launch { vm.keepCurrent() }

                advanceTimeBy(10_000)
                runCurrent()

                before to vm.uiState.value.lanes.map { it.id } shouldBe (listOf(5L) to listOf(5L, 6L))
            }
        }

        it("is read again only at the list's slow pace when nothing is moving, so a read that keeps failing isn't hammered") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            val main = request(uuid, 5, RequestState.Choose)
            val other = request(uuid, 6, RequestState.Choose, userId = 2)
            requests.serve(uuid, main, other)
            requests.answerAlways("request", UNREACHABLE, 6L)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

            vm.load()

            vm.uiState.value.pollInterval shouldBe 10_000L
        }
    }

    describe("the kinds the page offers to request") {
        it("lists each kind the library lacks and no request covers, for a reader who can start one") {
            val uuid = randomString()
            val requests = TestRequestsRepository().apply { serve(uuid, request(uuid, 5, kind = RequestKind.Ebook)) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))

            vm.load()

            vm.uiState.value.unrequested shouldBe listOf(RequestKind.Audiobook)
        }
    }

    describe("keeping the page current") {

        /** A page open on one request in [state], with the poll running in the background. */
        suspend fun TestScope.openPage(
            requests: TestRequestsRepository,
            uuid: String,
            time: TestTimeSource = TestTimeSource(),
        ): BookRequestViewModel {
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER), time)
            vm.load()
            backgroundScope.launch { vm.keepCurrent() }
            runCurrent()
            return vm
        }

        it("reads the listings and every moving lane again every two seconds") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository().apply { serve(uuid, request(uuid, 5, RequestState.OnItsWay)) }
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                openPage(requests, uuid)

                advanceTimeBy(1_999)
                val before = requests.callsTo("bookList").size
                advanceTimeBy(1)
                runCurrent()

                requests.callsTo("bookList").size - before shouldBe 1
                requests.callsTo("request").size shouldBe 2
            }
        }

        it("polls again and again while a lane keeps moving") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository().apply { serve(uuid, request(uuid, 5, RequestState.OnItsWay)) }
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                openPage(requests, uuid)

                advanceTimeBy(6_000)
                runCurrent()

                requests.callsTo("bookList").size shouldBe 4
            }
        }

        it("stops polling once nothing is moving") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val moving = request(uuid, 5, RequestState.OnItsWay)
                requests.serve(uuid, moving)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                openPage(requests, uuid)
                val done = moving.copy(state = RequestState.Ready, updatedAt = 200)
                requests.serve(uuid, done)

                advanceTimeBy(2_000)
                runCurrent()
                val settled = requests.calls.size
                advanceTimeBy(20_000)
                runCurrent()

                requests.calls.size shouldBe settled
            }
        }

        it("keeps polling a moving lane whose full read failed, and reads it again") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val main = request(uuid, 5, RequestState.OnItsWay)
                requests.answerAlways("bookList", RequestsResult.Success(listOf(listingOf(main))), uuid)
                requests.answer("request", UNREACHABLE, forFirstArg = 5L)
                requests.answerAlways("request", ok(main), 5L)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = openPage(requests, uuid)

                advanceTimeBy(2_000)
                runCurrent()

                vm.uiState.value.let { it.lanes.map { lane -> lane.id } to it.failure } shouldBe (listOf(5L) to null)
            }
        }

        it("keeps a lane in view when it finishes while the page stays open") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val chooser = request(uuid, 5, RequestState.Choose, kind = RequestKind.Ebook)
                val moving = request(uuid, 6, RequestState.OnItsWay, kind = RequestKind.Audiobook, userId = 2, updatedAt = 100)
                requests.serve(uuid, chooser, moving)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = openPage(requests, uuid)
                requests.serve(uuid, chooser, moving.copy(state = RequestState.Ready, updatedAt = 200))

                advanceTimeBy(2_000)
                runCurrent()

                vm.uiState.value.lanes.map { it.id to it.state } shouldBe
                    listOf(5L to RequestState.Choose, 6L to RequestState.Ready)
            }
        }

        it("reads a lane again when its listing says it changed on another client") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val chooser = request(uuid, 5, RequestState.Choose, updatedAt = 100)
                val moving = request(uuid, 6, RequestState.OnItsWay, userId = 2)
                requests.serve(uuid, chooser, moving)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = openPage(requests, uuid)
                val picked = chooser.copy(state = RequestState.Looking, updatedAt = 200)
                requests.serve(uuid, picked, moving)

                advanceTimeBy(2_000)
                runCurrent()

                vm.uiState.value.lanes.first { it.id == 5L }.state shouldBe RequestState.Looking
            }
        }

        it("picks up another reader's new request as a lane") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val main = request(uuid, 5, RequestState.OnItsWay)
                requests.serve(uuid, main)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = openPage(requests, uuid)
                val theirs = request(uuid, 6, RequestState.Looking, userId = 2)
                requests.serve(uuid, theirs, main)

                advanceTimeBy(2_000)
                runCurrent()

                vm.uiState.value.lanes.map { it.id } shouldBe listOf(5L, 6L)
            }
        }

        it("keeps the page when a poll fails") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val main = request(uuid, 5, RequestState.OnItsWay)
                requests.serve(uuid, main)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = openPage(requests, uuid)
                requests.answer("bookList", UNREACHABLE)

                advanceTimeBy(2_000)
                runCurrent()

                vm.uiState.value.let { it.failure to it.lanes.map { lane -> lane.id } } shouldBe (null to listOf(5L))
            }
        }

        it("waits while nothing moves and starts polling when a lane starts moving") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                val idle = request(uuid, 5, RequestState.Choose)
                requests.serve(uuid, idle)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = openPage(requests, uuid)
                advanceTimeBy(30_000)
                runCurrent()
                val whileIdle = requests.callsTo("bookList").size
                val moving = idle.copy(state = RequestState.OnItsWay, updatedAt = 300)
                requests.serve(uuid, moving)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                vm.load()

                advanceTimeBy(2_000)
                runCurrent()

                whileIdle to requests.callsTo("bookList").size shouldBe (1 to 3)
            }
        }

        it("survives a failed first load") {
            runTest {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                requests.answer("bookList", UNREACHABLE)
                requests.answer("wanters", UNREACHABLE)
                val vm = openPage(requests, uuid)
                requests.serve(uuid, request(uuid, 5, RequestState.OnItsWay))
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                vm.load()

                advanceTimeBy(2_000)
                runCurrent()

                requests.callsTo("bookList").size shouldBe 3
            }
        }
    }

    describe("writing to a request") {

        /** A page open on [lanes], with every lane's full read served. */
        fun openedPage(
            uuid: String,
            vararg lanes: BookRequest,
            viewer: RequestViewer = CHOOSER,
            requests: TestRequestsRepository = TestRequestsRepository(),
            online: Boolean = true,
            time: TestTimeSource = TestTimeSource(),
        ): Pair<BookRequestViewModel, TestRequestsRepository> {
            requests.serve(uuid, *lanes)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(viewer), time, online)
            vm.load()
            return vm to requests
        }

        context("while one write runs") {
            it("shows which control was pressed") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                requests.answer("choose", ok(request(uuid, 5, RequestState.Chosen)), CompletableDeferred())

                vm.choose(5, 11)

                vm.uiState.value.busy shouldBe Busy.Choosing(5, 11)
            }

            it("ignores a second write, which sends nothing") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.OnItsWay))
                requests.answer("swap", ok(request(uuid, 5, RequestState.Choose)), CompletableDeferred())
                vm.swap(5)

                vm.retry(5)
                vm.start(RequestKind.Audiobook)

                requests.calls.map { it.function }.filter { it in setOf("swap", "retry", "start") } shouldBe listOf("swap")
            }

            it("frees the controls once the write answers") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                val gate = CompletableDeferred<Unit>()
                requests.answer("choose", ok(request(uuid, 5, RequestState.Chosen)), gate)
                requests.serve(uuid, request(uuid, 5, RequestState.Chosen))
                vm.choose(5, 11)

                gate.complete(Unit)

                vm.uiState.value.busy shouldBe null
            }
        }

        it("sends nothing while the device is offline") {
            val uuid = randomString()
            val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose), online = false)

            vm.choose(5, 11)

            requests.callsTo("choose") shouldBe emptyList()
        }

        describe("a write that succeeds") {

            it("stores the answer as the lane's read") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                val answer = request(uuid, 5, RequestState.Chosen, updatedAt = 300)
                requests.answer("choose", ok(answer))
                requests.serve(uuid, answer)

                vm.choose(5, 11)

                vm.uiState.value.reads[5] shouldBe answer
            }

            it("folds the answer into the listings before the follow-up read answers") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                val answer = request(uuid, 5, RequestState.Chosen, updatedAt = 300)
                requests.answer("choose", ok(answer))
                requests.answer("bookList", RequestsResult.Success(emptyList<RequestListing>()), CompletableDeferred())

                vm.choose(5, 11)

                vm.uiState.value.listings!!.single().request shouldBe answer
            }

            it("reads the listings again") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                requests.answer("choose", ok(request(uuid, 5, RequestState.Chosen)))
                val before = requests.callsTo("bookList").size

                vm.choose(5, 11)

                requests.callsTo("bookList").size - before shouldBe 1
            }

            it("sends the lane's id and the version's id to choose") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                requests.answer("choose", ok(request(uuid, 5, RequestState.Chosen)))

                vm.choose(5, 11)

                requests.callsTo("choose").map { it.args } shouldBe listOf(listOf<Any?>(5L, 11L))
            }
        }

        describe("starting a request") {

            it("makes a request on an empty page the main lane") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid)
                val started = request(uuid, 9, RequestState.Looking)
                requests.answer("start", ok(started))
                requests.serve(uuid, started)

                vm.start(RequestKind.Ebook)

                vm.uiState.value.let { it.mainId to it.lanes.map { lane -> lane.id } } shouldBe (9L to listOf(9L))
            }

            it("still becomes a lane, starts polling and stops offering its kind when the follow-up read fails") {
                runTest {
                    val uuid = randomString()
                    val (vm, requests) = openedPage(uuid)
                    backgroundScope.launch { vm.keepCurrent() }
                    val started = request(uuid, 9, RequestState.Looking)
                    requests.answer("start", ok(started))
                    requests.answer("bookList", UNREACHABLE)
                    requests.answerAlways("request", ok(started), 9L)

                    vm.start(RequestKind.Ebook)
                    runCurrent()

                    vm.uiState.value.let { Triple(it.lanes.map { lane -> lane.id }, it.pollInterval, it.unrequested) } shouldBe
                        Triple(listOf(9L), 2_000L, listOf(RequestKind.Audiobook))
                }
            }

            it("sends the book and the kind") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid)
                requests.answer("start", ok(request(uuid, 9)))

                vm.start(RequestKind.Audiobook)

                requests.callsTo("start").map { it.args } shouldBe listOf(listOf<Any?>(uuid, RequestKind.Audiobook))
            }

            it("is refused for a reader who can't start requests, with nothing sent") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, viewer = BLOCKED)

                vm.start(RequestKind.Ebook)

                requests.callsTo("start") shouldBe emptyList()
            }

            it("says why in the message when it fails") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid)
                requests.answer("start", RequestsResult.Failure(RequestsError.Http(429, null)))

                vm.start(RequestKind.Ebook)

                vm.uiState.value.message?.text shouldBe "You have a lot of requests going. Try again in a few minutes."
            }
        }

        describe("the writes only a reader who can pick versions may make") {
            it("sends none for a reader who can't") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.OnItsWay), viewer = ASKER)

                vm.choose(5, 11)
                vm.swap(5)
                vm.retry(5)
                vm.takeCover(5)
                vm.keepCover(5)

                requests.calls.map { it.function }.filter { it in setOf("choose", "swap", "retry", "takeCover", "keepCover") } shouldBe emptyList()
            }

            it("sends each for a reader who can") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.OnItsWay))
                listOf("swap", "retry", "takeCover", "keepCover").forEach { requests.answer(it, ok(request(uuid, 5))) }

                vm.swap(5)
                vm.retry(5)
                vm.takeCover(5)
                vm.keepCover(5)

                requests.calls.map { it.function }.filter { it in setOf("swap", "retry", "takeCover", "keepCover") } shouldBe
                    listOf("swap", "retry", "takeCover", "keepCover")
            }
        }

        describe("a write that fails") {

            it("says what failed and why, and frees the controls") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                requests.answer("choose", RequestsResult.Failure(RequestsError.Http(409, "Already chosen")))

                vm.choose(5, 11)

                vm.uiState.value.let { it.message?.text to it.busy } shouldBe ("Couldn’t choose that version: Already chosen" to null)
            }

            it("doesn't read again after a failed choose") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Choose))
                requests.answer("choose", RequestsResult.Failure(RequestsError.Unreachable))
                val calls = requests.calls.size

                vm.choose(5, 11)

                requests.calls.size - calls shouldBe 1
            }

            it("reads the lane and the listings again after a failed cover write, since the request may have moved on") {
                val uuid = randomString()
                val (vm, requests) = openedPage(uuid, request(uuid, 5, RequestState.Ready))
                requests.answer("takeCover", RequestsResult.Failure(RequestsError.Http(409, "No offer")))
                val before = requests.calls.size

                vm.takeCover(5)

                requests.calls.drop(before).map { it.function } shouldBe listOf("takeCover", "request", "bookList")
            }
        }

        describe("a read that started before a write") {

            it("is dropped by a poll that answers after the write") {
                runTest {
                    val uuid = randomString()
                    val chooser = request(uuid, 5, RequestState.Choose, updatedAt = 100)
                    val moving = request(uuid, 6, RequestState.OnItsWay, userId = 2)
                    val (vm, requests) = openedPage(uuid, chooser, moving)
                    backgroundScope.launch { vm.keepCurrent() }
                    val stale = RequestsResult.Success(listOf(listingOf(chooser), listingOf(moving)))
                    val gate = CompletableDeferred<Unit>()
                    requests.answer("bookList", stale, gate)
                    advanceTimeBy(2_000)
                    runCurrent()
                    val answer = chooser.copy(state = RequestState.Chosen, updatedAt = 300)
                    requests.answer("choose", ok(answer))
                    requests.serve(uuid, answer, moving)
                    vm.choose(5, 11)

                    gate.complete(Unit)
                    runCurrent()

                    vm.uiState.value.listings!!.first { it.request.id == 5L }.request.state shouldBe RequestState.Chosen
                }
            }

            it("is dropped by a full reload that answers after the write") {
                val uuid = randomString()
                val chooser = request(uuid, 5, RequestState.Choose, updatedAt = 100)
                val (vm, requests) = openedPage(uuid, chooser)
                val gate = CompletableDeferred<Unit>()
                requests.answer("bookList", RequestsResult.Success(listOf(listingOf(chooser))), gate)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                vm.load()
                val answer = chooser.copy(state = RequestState.Chosen, updatedAt = 300)
                requests.answer("choose", ok(answer))
                requests.serve(uuid, answer)
                vm.choose(5, 11)

                gate.complete(Unit)

                vm.uiState.value.listings!!.single().request.state shouldBe RequestState.Chosen
            }

            it("only fills the lanes the write did not answer, when a reload was raced after its listings") {
                val uuid = randomString()
                val main = request(uuid, 5, RequestState.Choose, updatedAt = 100)
                val other = request(uuid, 6, RequestState.Choose, userId = 2, updatedAt = 100)
                val (vm, requests) = openedPage(uuid, main, other)
                val gate = CompletableDeferred<Unit>()
                requests.answer("request", ok(main), gate, forFirstArg = 5L)
                requests.answer("request", ok(other), forFirstArg = 6L)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                vm.load()
                val answer = other.copy(state = RequestState.Chosen, updatedAt = 300)
                requests.answer("choose", ok(answer))
                requests.serve(uuid, main, answer)
                vm.choose(6, 11)

                gate.complete(Unit)

                vm.uiState.value.reads[6] shouldBe answer
            }

            it("keeps the lane a write started while the reload was out, though the reload listed none of it") {
                val uuid = randomString()
                val main = request(uuid, 5, RequestState.Choose, updatedAt = 100)
                val (vm, requests) = openedPage(uuid, main)
                val gate = CompletableDeferred<Unit>()
                requests.answer("request", ok(main), gate, forFirstArg = 5L)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                vm.load()
                val started = request(uuid, 9, RequestState.Looking, kind = RequestKind.Audiobook)
                requests.answer("start", ok(started))
                requests.serve(uuid, main, started)
                vm.start(RequestKind.Audiobook)

                gate.complete(Unit)

                vm.uiState.value.lanes.map { it.id } shouldBe listOf(5L, 9L)
            }

            it("still shows the error for a first load a write raced, when its main read fails") {
                val uuid = randomString()
                val requests = TestRequestsRepository()
                requests.answerAlways("bookList", RequestsResult.Success(listOf(listingOf(request(uuid, 5)))), uuid)
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val gate = CompletableDeferred<Unit>()
                requests.answer("request", RequestsResult.Failure(RequestsError.Unreachable), gate, forFirstArg = 5L)
                val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER))
                vm.load()
                requests.answer("start", ok(request(uuid, 9, RequestState.Looking, kind = RequestKind.Audiobook)))
                vm.start(RequestKind.Audiobook)

                gate.complete(Unit)

                vm.uiState.value.failure shouldBe RequestsError.Unreachable
            }
        }
    }

    describe("cancelling a request") {

        /** A page open on [lanes] for [viewer], with the clock at [now] seconds. */
        fun cancellablePage(
            uuid: String,
            vararg lanes: BookRequest,
            viewer: RequestViewer = CHOOSER,
            now: Long = 1_000,
            time: TestTimeSource = TestTimeSource(now * 1000),
        ): Pair<BookRequestViewModel, TestRequestsRepository> {
            val requests = TestRequestsRepository().apply { serve(uuid, *lanes) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(viewer), time)
            vm.load()
            return vm to requests
        }

        it("sends the cancel for a request that is still going") {
            val uuid = randomString()
            val (vm, requests) = cancellablePage(uuid, request(uuid, 5, RequestState.Looking))
            requests.answer("cancel", ok(request(uuid, 5, RequestState.Cancelled)))

            vm.cancel(5)

            requests.callsTo("cancel").map { it.args } shouldBe listOf(listOf<Any?>(5L))
        }

        it("sends nothing for a request that can no longer be cancelled") {
            val uuid = randomString()
            val finished = request(uuid, 5, RequestState.OnItsWay).copy(progress = 1.0)
            val (vm, requests) = cancellablePage(uuid, finished, request(uuid, 6, RequestState.Stuck))

            vm.cancel(5)
            vm.cancel(6)

            requests.callsTo("cancel") shouldBe emptyList()
        }

        it("lets a reader who can pick versions cancel another reader's request, and an asker only their own") {
            val uuid = randomString()
            val theirs = request(uuid, 5, RequestState.Looking, userId = 2)
            val mine = request(uuid, 6, RequestState.Looking, userId = ASKER.id)
            val (asker, askerRequests) = cancellablePage(uuid, theirs, mine, viewer = ASKER)
            val (chooser, chooserRequests) = cancellablePage(uuid, theirs, viewer = CHOOSER)
            listOf(askerRequests, chooserRequests).forEach { it.answer("cancel", ok(request(uuid, 5, RequestState.Cancelled))) }

            asker.cancel(5)
            chooser.cancel(5)

            listOf(askerRequests.callsTo("cancel").size, chooserRequests.callsTo("cancel").size) shouldBe listOf(0, 1)
        }

        describe("the moment the answer arrives") {
            it("takes the cancelled answer, frees the controls and offers Undo before the listings read answers") {
                val uuid = randomString()
                val (vm, requests) = cancellablePage(uuid, request(uuid, 5, RequestState.Looking, kind = RequestKind.Ebook), now = 1_000)
                val cancelled = request(uuid, 5, RequestState.Cancelled, kind = RequestKind.Ebook).copy(cancelAt = 1_010)
                requests.answer("cancel", ok(cancelled))
                requests.answer("bookList", UNREACHABLE, CompletableDeferred())

                vm.cancel(5)

                vm.uiState.value.let { Triple(it.reads[5], it.busy, it.message?.copy(id = 0)) } shouldBe
                    Triple(cancelled, null, RequestMessage("Ebook request cancelled", undoRequestId = 5, postedAtMillis = 1_000_000, textDeadlineMillis = 1_008_500, undoDeadlineMillis = 1_008_500))
            }

            it("shows an audiobook request's own kind") {
                val uuid = randomString()
                val (vm, requests) = cancellablePage(uuid, request(uuid, 5, RequestState.Looking, kind = RequestKind.Audiobook))
                requests.answer("cancel", ok(request(uuid, 5, RequestState.Cancelled, kind = RequestKind.Audiobook).copy(cancelAt = 1_010)))
                requests.serve(uuid, request(uuid, 5, RequestState.Cancelled, kind = RequestKind.Audiobook))

                vm.cancel(5)

                vm.uiState.value.message?.text shouldBe "Audiobook request cancelled"
            }

            it("offers no Undo when the answer has no cancel_at") {
                val uuid = randomString()
                val (vm, requests) = cancellablePage(uuid, request(uuid, 5, RequestState.Looking))
                requests.answer("cancel", ok(request(uuid, 5, RequestState.Cancelled)))
                requests.serve(uuid, request(uuid, 5, RequestState.Cancelled))

                vm.cancel(5)

                vm.uiState.value.message shouldBe null
            }
        }

        describe("a cancel that fails") {
            it("says why, with no Undo, and reads the request again") {
                val uuid = randomString()
                val (vm, requests) = cancellablePage(uuid, request(uuid, 5, RequestState.Looking))
                requests.answer("cancel", RequestsResult.Failure(RequestsError.Http(409, "Too late")))
                val before = requests.calls.size

                vm.cancel(5)

                vm.uiState.value.message?.let { it.text to it.undoRequestId } shouldBe ("Couldn’t cancel: Too late" to null)
                requests.calls.drop(before).map { it.function } shouldBe listOf("cancel", "request", "bookList")
            }
        }
    }

    describe("undoing a cancel") {

        /** A page where request 5 was just cancelled, with Undo showing. */
        fun cancelledPage(uuid: String, time: TestTimeSource = TestTimeSource(1_000_000)): Pair<BookRequestViewModel, TestRequestsRepository> {
            val looking = request(uuid, 5, RequestState.Looking)
            val requests = TestRequestsRepository().apply { serve(uuid, looking) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER), time)
            vm.load()
            val cancelled = looking.copy(state = RequestState.Cancelled, cancelAt = 1_010)
            requests.answer("cancel", ok(cancelled))
            requests.serve(uuid, cancelled)
            vm.cancel(5)
            return vm to requests
        }

        it("restores whatever the server returns") {
            val uuid = randomString()
            val (vm, requests) = cancelledPage(uuid)
            val restored = request(uuid, 5, RequestState.Choose, updatedAt = 500)
            requests.answer("undoCancel", ok(restored))
            requests.serve(uuid, restored)

            vm.undo()

            vm.uiState.value.let { it.reads[5] to it.message } shouldBe (restored to null)
        }

        it("sends nothing and keeps the message while another write runs") {
            val uuid = randomString()
            val (vm, requests) = cancelledPage(uuid)
            requests.answer("start", ok(request(uuid, 9, kind = RequestKind.Audiobook)), CompletableDeferred())
            vm.start(RequestKind.Audiobook)

            vm.undo()

            requests.callsTo("undoCancel").size to vm.uiState.value.message?.undoRequestId shouldBe (0 to 5L)
        }

        it("says why when it fails for a reason another press may get past, keeping the Undo while its window is open") {
            val uuid = randomString()
            val (vm, requests) = cancelledPage(uuid)
            requests.answer("undoCancel", RequestsResult.Failure(RequestsError.Http(503, "Busy")))

            vm.undo()

            vm.uiState.value.message?.let { it.text to it.undoRequestId } shouldBe ("Couldn’t undo: Busy" to 5L)
        }

        it("says why and offers no Undo when the server refuses it, since pressing again changes nothing") {
            val uuid = randomString()
            val (vm, requests) = cancelledPage(uuid)
            requests.answer("undoCancel", RequestsResult.Failure(RequestsError.Http(410, "Too late")))

            vm.undo()

            vm.uiState.value.message?.let { Triple(it.text, it.undoRequestId, it.undoDeadlineMillis) } shouldBe
                Triple("Couldn’t undo: Too late", null, null)
        }

        it("keeps the Undo, with its own deadline, when another write fails") {
            val uuid = randomString()
            val time = TestTimeSource(1_000_000)
            val (vm, requests) = cancelledPage(uuid, time)
            time.advanceBy(3_000)
            requests.answer("start", RequestsResult.Failure(RequestsError.Http(429, null)))

            vm.start(RequestKind.Audiobook)

            vm.uiState.value.message?.copy(id = 0) shouldBe RequestMessage(
                "You have a lot of requests going. Try again in a few minutes.",
                undoRequestId = 5, postedAtMillis = 1_003_000, textDeadlineMillis = 1_008_500, undoDeadlineMillis = 1_008_500,
            )
        }

        it("shows a failure for at least the usual time when the Undo is about to run out") {
            val uuid = randomString()
            val time = TestTimeSource(1_000_000)
            val (vm, requests) = cancelledPage(uuid, time)
            time.advanceBy(8_400)
            requests.answer("start", RequestsResult.Failure(RequestsError.Http(429, null)))

            vm.start(RequestKind.Audiobook)

            vm.uiState.value.message!!.textRemainingMillis(1_008_400) shouldBe 4_000
        }

        it("sends nothing while the device is offline") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            val looking = request(uuid, 5, RequestState.Looking)
            requests.serve(uuid, looking)
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val monitor = TestConnectivityMonitor(true)
            val vm = BookRequestViewModel(uuid, requests, libraryWith(uuid), authAs(CHOOSER), TestTimeSource(1_000_000), monitor) { RequestWording() }
            vm.load()
            requests.answer("cancel", ok(looking.copy(state = RequestState.Cancelled, cancelAt = 1_010)))
            requests.serve(uuid, looking.copy(state = RequestState.Cancelled))
            vm.cancel(5)
            monitor.isOnline.value = false

            vm.undo()

            requests.callsTo("undoCancel").size to vm.uiState.value.message?.undoRequestId shouldBe (0 to 5L)
        }
    }

    describe("how long a message has left") {
        it("is the time to its deadline, so a message shown again later has less left") {
            val message = RequestMessage("x", undoRequestId = 1, textDeadlineMillis = 10_000, undoDeadlineMillis = 7_000)

            listOf(message.textRemainingMillis(4_000), message.textRemainingMillis(9_000), message.undoRemainingMillis(4_000)) shouldBe
                listOf(6_000L, 1_000L, 3_000L)
        }

        it("counts a longer accessibility timeout from when the message was posted, and never starts it over") {
            val message = RequestMessage("x", postedAtMillis = 1_000, textDeadlineMillis = 5_000)

            listOf(
                message.textRemainingMillis(nowMillis = 5_000, recommendedMillis = 12_000),
                message.textRemainingMillis(nowMillis = 10_000, recommendedMillis = 12_000),
                message.textRemainingMillis(nowMillis = 13_500, recommendedMillis = 12_000),
            ) shouldBe listOf(8_000L, 3_000L, 0L)
        }

        it("is none once the deadline has passed, and for an Undo that was never offered") {
            val message = RequestMessage("x", textDeadlineMillis = 10_000)

            listOf(message.textRemainingMillis(12_000), message.undoRemainingMillis(0)) shouldBe listOf(0L, 0L)
        }
    }

    describe("offering Request again") {

        /** A page with one cancelled lane per entry of [cancelAts] (Unix seconds), the clock at 1000 s, and the clock loop running. */
        suspend fun TestScope.cancelledLanes(vararg cancelAts: Long, viewer: RequestViewer = CHOOSER, time: TestTimeSource = TestTimeSource(1_000_000)): Pair<BookRequestViewModel, List<BookRequest>> {
            val uuid = randomString()
            val lanes = cancelAts.mapIndexed { i, at -> request(uuid, 10L + i, RequestState.Cancelled).copy(cancelAt = at, updatedAt = 900L + i) }
            val requests = TestRequestsRepository().apply { serve(uuid, *lanes.toTypedArray()) }
            requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
            val vm = sut(uuid, requests, libraryWith(uuid), authAs(viewer), time)
            vm.load()
            backgroundScope.launch { vm.keepCurrent() }
            runCurrent()
            return vm to lanes
        }

        it("appears once the Undo window passes, with no reload") {
            runTest {
                val time = TestTimeSource(1_000_000)
                val (vm, lanes) = cancelledLanes(1_010, time = time)
                val before = vm.uiState.value.offersRequestAgain(lanes.single())

                time.advanceBy(10_000)
                advanceTimeBy(10_300)
                runCurrent()

                before to vm.uiState.value.offersRequestAgain(lanes.single()) shouldBe (false to true)
            }
        }

        it("appears on the second cancelled lane too, when its window closes later") {
            runTest {
                val time = TestTimeSource(1_000_000)
                val (vm, lanes) = cancelledLanes(1_010, 1_030, time = time)

                time.advanceBy(10_000)
                advanceTimeBy(10_300)
                runCurrent()
                val afterFirst = lanes.map { vm.uiState.value.offersRequestAgain(it) }
                time.advanceBy(20_000)
                advanceTimeBy(20_300)
                runCurrent()

                afterFirst to lanes.map { vm.uiState.value.offersRequestAgain(it) } shouldBe (listOf(true, false) to listOf(true, true))
            }
        }

        it("is held back from a reader who can't start requests") {
            runTest {
                val time = TestTimeSource(1_000_000)
                val (vm, lanes) = cancelledLanes(1_010, viewer = BLOCKED, time = time)

                time.advanceBy(10_000)
                advanceTimeBy(10_300)
                runCurrent()

                vm.uiState.value.offersRequestAgain(lanes.single()) shouldBe false
            }
        }

        it("is held back while the viewer has that kind going") {
            runTest {
                val uuid = randomString()
                val cancelled = request(uuid, 10, RequestState.Cancelled, kind = RequestKind.Ebook).copy(cancelAt = null)
                val going = request(uuid, 11, RequestState.Looking, kind = RequestKind.Ebook, userId = CHOOSER.id)
                val requests = TestRequestsRepository().apply { serve(uuid, going, cancelled) }
                requests.answer("wanters", RequestsResult.Success(emptyList<Wanter>()))
                val vm = sut(uuid, requests, libraryWith(uuid), authAs(CHOOSER), TestTimeSource(1_000_000))

                vm.load()

                vm.uiState.value.offersRequestAgain(cancelled) shouldBe false
            }
        }
    }
})
