package com.omnibus.omnibus.feature.requests.page

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.requests.BookRequest
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
import com.omnibus.omnibus.data.requests.randomWanter
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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
    ) = BookRequestViewModel(uuid, requests, library, auth, time)

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

                vm.uiState.value.lanes.map { it.id } shouldBe listOf(5L)
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
})
