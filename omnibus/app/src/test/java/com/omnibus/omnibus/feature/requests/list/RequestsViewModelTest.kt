package com.omnibus.omnibus.feature.requests.list

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.TestRequestsRepository
import com.omnibus.omnibus.data.requests.randomBookRequest
import com.omnibus.omnibus.data.requests.randomRequestListing
import com.omnibus.omnibus.feature.requests.RequestGroupKey
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private val CHOOSER = RequestViewer(id = 1, canStart = true, canChoose = true)
private val ASKER = RequestViewer(id = 1, canStart = true, canChoose = false)

private fun authAs(viewer: RequestViewer) = TestAuthRepository().apply {
    currentUserState.value = randomCurrentUser().copy(
        id = viewer.id,
        bookRequests = true,
        canStartRequests = viewer.canStart,
        canChooseRequestVersions = viewer.canChoose,
    )
}

private fun listing(uuid: String, state: RequestState, id: Long = 1): RequestListing = randomRequestListing().let {
    it.copy(
        request = randomBookRequest().copy(id = id, bookUuid = uuid, state = state),
        book = randomBook().copy(uniqueIdentifier = uuid),
    )
}

private fun ok(vararg listings: RequestListing) = RequestsResult.Success(listings.toList())

private val UNREACHABLE = RequestsResult.Failure(RequestsError.Unreachable)

@OptIn(ExperimentalCoroutinesApi::class)
class RequestsViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        requests: TestRequestsRepository = TestRequestsRepository(),
        auth: TestAuthRepository = TestAuthRepository(),
        online: Boolean = true,
    ) = RequestsViewModel(requests, auth, TestConnectivityMonitor(online))

    describe("loading the list") {

        it("groups every reader's requests for the signed-in viewer") {
            val requests = TestRequestsRepository()
            requests.answer("list", ok(listing("a", RequestState.Choose, 1), listing("b", RequestState.Looking, 2)))
            val vm = sut(requests, authAs(CHOOSER))

            vm.load()

            vm.uiState.value.groups.map { it.key to it.items.map { item -> item.book.uuid } } shouldBe
                listOf(RequestGroupKey.NeedsYou to listOf("a"), RequestGroupKey.Looking to listOf("b"))
        }

        it("files a pick still to make under looking for a viewer who can't pick versions") {
            val requests = TestRequestsRepository()
            requests.answer("list", ok(listing("a", RequestState.Choose)))
            val vm = sut(requests, authAs(ASKER))

            vm.load()

            vm.uiState.value.groups.map { it.key } shouldBe listOf(RequestGroupKey.Looking)
        }

        it("shows the empty list as loaded, with no rows") {
            val requests = TestRequestsRepository()
            requests.answer("list", ok())
            val vm = sut(requests, authAs(CHOOSER))

            vm.load()

            vm.uiState.value.let { it.listings to it.failure } shouldBe (emptyList<RequestListing>() to null)
        }

        context("when the first load fails") {
            it("shows the failure, and loads when the retry succeeds") {
                val requests = TestRequestsRepository()
                requests.answer("list", RequestsResult.Failure(RequestsError.Http(500, "boom")))
                val vm = sut(requests, authAs(CHOOSER))
                vm.load()
                val failed = vm.uiState.value.failure
                requests.answer("list", ok(listing("a", RequestState.Looking)))

                vm.load()

                failed to vm.uiState.value.let { it.failure to it.listings?.size } shouldBe (RequestsError.Http(500, "boom") to (null to 1))
            }
        }

        context("when a later read fails") {
            it("keeps the rows after a failed refresh") {
                val requests = TestRequestsRepository()
                requests.answer("list", ok(listing("a", RequestState.Looking)))
                val vm = sut(requests, authAs(CHOOSER))
                vm.load()
                requests.answer("list", UNREACHABLE)

                vm.refresh()

                vm.uiState.value.let { Triple(it.listings?.size, it.failure, it.isRefreshing) } shouldBe Triple(1, null, false)
            }

            it("keeps the rows after a failed poll") {
                runTest {
                    val requests = TestRequestsRepository()
                    requests.answer("list", ok(listing("a", RequestState.Looking)))
                    val vm = sut(requests, authAs(CHOOSER))
                    vm.load()
                    backgroundScope.launch { vm.keepCurrent() }
                    requests.answer("list", UNREACHABLE)

                    advanceTimeBy(10_000)
                    runCurrent()

                    vm.uiState.value.let { it.listings?.size to it.failure } shouldBe (1 to null)
                }
            }
        }
    }

    describe("a session confirmed after the list opened") {
        it("files the picks for the user once they arrive, without another read") {
            val requests = TestRequestsRepository()
            requests.answer("list", ok(listing("a", RequestState.Choose)))
            val auth = TestAuthRepository()
            val vm = sut(requests, auth)
            vm.load()
            val before = vm.uiState.value.groups.map { it.key }

            auth.currentUserState.value = randomCurrentUser().copy(id = 1, bookRequests = true, canStartRequests = true, canChooseRequestVersions = true)

            before to vm.uiState.value.groups.map { it.key } shouldBe (listOf(RequestGroupKey.Looking) to listOf(RequestGroupKey.NeedsYou))
        }

        it("offers no request when the server has book requests off for the reader") {
            val user = randomCurrentUser().copy(bookRequests = false, canStartRequests = true, canChooseRequestVersions = true)
            val auth = TestAuthRepository()
            val vm = sut(auth = auth)

            auth.currentUserState.value = user

            vm.uiState.value.viewer shouldBe RequestViewer(user.id, canStart = false, canChoose = false)
        }
    }

    describe("a read that answers after a newer one") {
        it("is dropped, so a slow poll can't overwrite a refresh") {
            runTest {
                val requests = TestRequestsRepository()
                val gate = CompletableDeferred<Unit>()
                requests.answer("list", ok(listing("old", RequestState.OnItsWay)))
                val vm = sut(requests, authAs(CHOOSER))
                vm.load()
                backgroundScope.launch { vm.keepCurrent() }
                requests.answer("list", ok(listing("stale", RequestState.OnItsWay)), gate)
                advanceTimeBy(10_000)
                runCurrent()
                requests.answer("list", ok(listing("fresh", RequestState.OnItsWay)))
                vm.refresh()

                gate.complete(Unit)
                runCurrent()

                vm.uiState.value.listings!!.map { it.book.uuid } shouldBe listOf("fresh")
            }
        }
    }

    describe("a read that fails while a slower one is still out") {
        it("doesn't drop the slower one's rows when they arrive, nor leave the error over them") {
            runTest {
                val requests = TestRequestsRepository()
                val gate = CompletableDeferred<Unit>()
                requests.answer("list", ok(listing("slow", RequestState.OnItsWay)), gate)
                val vm = sut(requests, authAs(CHOOSER))
                vm.load()
                requests.answer("list", UNREACHABLE)
                vm.refresh()

                gate.complete(Unit)
                runCurrent()

                vm.uiState.value.let { state -> state.listings?.map { it.book.uuid } to state.failure } shouldBe (listOf("slow") to null)
            }
        }
    }

    describe("keeping the list current") {

        it("reads again every ten seconds while anything moves") {
            runTest {
                val requests = TestRequestsRepository()
                requests.answerAlways("list", ok(listing("a", RequestState.OnItsWay)))
                val vm = sut(requests, authAs(CHOOSER))
                vm.load()
                backgroundScope.launch { vm.keepCurrent() }
                runCurrent()

                advanceTimeBy(9_999)
                val before = requests.callsTo("list").size
                advanceTimeBy(1)
                runCurrent()

                before to requests.callsTo("list").size shouldBe (1 to 2)
            }
        }

        it("waits while nothing moves, and starts again when a refresh finds something moving") {
            runTest {
                val requests = TestRequestsRepository()
                requests.answer("list", ok(listing("a", RequestState.Ready)))
                val vm = sut(requests, authAs(CHOOSER))
                vm.load()
                backgroundScope.launch { vm.keepCurrent() }
                runCurrent()
                advanceTimeBy(60_000)
                runCurrent()
                val idle = requests.callsTo("list").size
                requests.answerAlways("list", ok(listing("a", RequestState.OnItsWay)))
                vm.refresh()

                advanceTimeBy(10_000)
                runCurrent()

                idle to requests.callsTo("list").size shouldBe (1 to 3)
            }
        }

        it("survives a failed first load") {
            runTest {
                val requests = TestRequestsRepository()
                requests.answer("list", UNREACHABLE)
                val vm = sut(requests, authAs(CHOOSER))
                vm.load()
                backgroundScope.launch { vm.keepCurrent() }
                runCurrent()
                requests.answerAlways("list", ok(listing("a", RequestState.OnItsWay)))
                vm.load()

                advanceTimeBy(10_000)
                runCurrent()

                requests.callsTo("list").size shouldBe 3
            }
        }
    }

    describe("folding the groups") {
        it("starts with only Cancelled folded") {
            sut(auth = authAs(CHOOSER)).uiState.value.folded shouldBe setOf(RequestGroupKey.Cancelled)
        }

        it("flips a group each time it is toggled") {
            val vm = sut(auth = authAs(CHOOSER))

            vm.toggleFold(RequestGroupKey.Cancelled)
            val unfolded = vm.uiState.value.folded
            vm.toggleFold(RequestGroupKey.Ready)
            val readyFolded = vm.uiState.value.folded

            unfolded to readyFolded shouldBe (emptySet<RequestGroupKey>() to setOf(RequestGroupKey.Ready))
        }
    }

    describe("the Request a book button") {
        it("is offered only to a reader who can start requests, once the list has loaded and while online") {
            val requests = TestRequestsRepository()
            requests.answerAlways("list", ok())
            val canStart = sut(requests, authAs(CHOOSER), online = true)
            val cannotStart = sut(requests, authAs(CHOOSER.copy(canStart = false)), online = true)
            val offline = sut(requests, authAs(CHOOSER), online = false)
            val beforeLoad = canStart.uiState.value.canRequest
            listOf(canStart, cannotStart, offline).forEach { it.load() }

            listOf(beforeLoad, canStart.uiState.value.canRequest, cannotStart.uiState.value.canRequest, offline.uiState.value.canRequest) shouldBe
                listOf(false, true, false, false)
        }
    }
})
