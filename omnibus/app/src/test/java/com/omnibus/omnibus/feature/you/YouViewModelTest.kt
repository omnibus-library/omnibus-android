package com.omnibus.omnibus.feature.you

import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.TestRequestsRepository
import com.omnibus.omnibus.data.requests.randomBookRequest
import com.omnibus.omnibus.data.requests.randomRequestListing
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun authAs(bookRequests: Boolean = true, canChoose: Boolean = true) = TestAuthRepository().apply {
    currentUserState.value = randomCurrentUser().copy(bookRequests = bookRequests, canChooseRequestVersions = canChoose)
}

private fun picking(vararg uuids: String): RequestsResult<List<RequestListing>> = RequestsResult.Success(
    uuids.mapIndexed { i, uuid ->
        randomRequestListing().copy(
            request = randomBookRequest().copy(id = i + 1L, bookUuid = uuid, state = RequestState.Choose),
            book = randomBook().copy(uniqueIdentifier = uuid),
        )
    },
)

@OptIn(ExperimentalCoroutinesApi::class)
class YouViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        requests: TestRequestsRepository = TestRequestsRepository(),
        auth: TestAuthRepository = authAs(),
        libraries: LibrariesRepository = TestLibrariesRepository(),
    ) = YouViewModel(requests, auth, libraries)

    describe("the account card's library") {
        context("when the active library gains a username") {
            it("TC-6: shows the active library, then the same library with the username") {
                val active = randomLibrary().copy(isActive = true, username = null)
                val libraries = TestLibrariesRepository(listOf(active))
                val vm = sut(libraries = libraries)

                vm.uiState.test {
                    awaitItem().library shouldBe active
                    libraries.setUsername(active.id, "hoon")
                    awaitItem().library shouldBe active.copy(username = "hoon")
                }
            }
        }

        context("when there is no active library") {
            it("TC-7: shows no library") {
                sut(libraries = TestLibrariesRepository()).uiState.value.library shouldBe null
            }

            it("TC-7: still shows the Requests row") {
                val vm = sut(auth = authAs(bookRequests = true), libraries = TestLibrariesRepository())

                vm.shown()

                vm.uiState.value.showsRequests shouldBe true
            }
        }

        context("when reading the active library fails") {
            it("TC-7: shows no library") {
                val unreadable = object : LibrariesRepository by TestLibrariesRepository() {
                    override fun getActiveLibraryFlow(): Flow<Library?> = flow { throw IOException("unreadable") }
                }

                sut(libraries = unreadable).uiState.value.library shouldBe null
            }

            it("TC-7: still shows the Requests row") {
                val unreadable = object : LibrariesRepository by TestLibrariesRepository() {
                    override fun getActiveLibraryFlow(): Flow<Library?> = flow { throw IOException("unreadable") }
                }
                val vm = sut(auth = authAs(bookRequests = true), libraries = unreadable)

                vm.shown()

                vm.uiState.value.showsRequests shouldBe true
            }
        }
    }

    describe("showing the Requests row") {
        it("is shown only when the server has book requests on for the reader") {
            val on = sut(auth = authAs(bookRequests = true))
            val off = sut(auth = authAs(bookRequests = false))

            on.shown()
            off.shown()

            listOf(on.uiState.value.showsRequests, off.uiState.value.showsRequests) shouldBe listOf(true, false)
        }
    }

    describe("a session not known yet") {
        it("leaves the row undecided until the session is, rather than saying it is off") {
            val vm = sut(auth = TestAuthRepository())

            vm.shown()

            vm.uiState.value.showsRequests shouldBe null
        }
    }

    describe("a session that is confirmed after the screen is shown") {
        it("shows the row and the count once the user arrives") {
            val requests = TestRequestsRepository()
            requests.answer("list", picking("a", "b"))
            val auth = TestAuthRepository()
            val vm = sut(requests, auth)
            vm.shown()
            val before = vm.uiState.value

            auth.currentUserState.value = randomCurrentUser().copy(bookRequests = true, canChooseRequestVersions = true)

            before to vm.uiState.value shouldBe (YouUIState(null, 0) to YouUIState(true, 2))
        }
    }

    describe("the count of books waiting on the reader") {

        it("counts the books waiting on a reader who can pick versions") {
            val requests = TestRequestsRepository()
            requests.answer("list", picking("a", "b"))
            val vm = sut(requests)

            vm.shown()

            vm.uiState.value.needsYou shouldBe 2
        }

        it("reads the count again every time the You root is shown") {
            val requests = TestRequestsRepository()
            requests.answer("list", picking("a"))
            val vm = sut(requests)
            vm.shown()
            val first = vm.uiState.value.needsYou
            requests.answer("list", picking("a", "b", "c"))

            vm.shown()

            first to vm.uiState.value.needsYou shouldBe (1 to 3)
        }

        it("keeps the newest read's count when an older one answers last") {
            val requests = TestRequestsRepository()
            val gate = CompletableDeferred<Unit>()
            requests.answer("list", picking("a"), gate)
            val vm = sut(requests)
            vm.shown()
            requests.answer("list", picking("a", "b", "c"))
            vm.shown()

            gate.complete(Unit)

            vm.uiState.value.needsYou shouldBe 3
        }

        it("shows no count, never a stale one, when the read fails") {
            val requests = TestRequestsRepository()
            requests.answer("list", picking("a"))
            val vm = sut(requests)
            vm.shown()
            requests.answer("list", RequestsResult.Failure(RequestsError.Unreachable))

            vm.shown()

            vm.uiState.value.needsYou shouldBe 0
        }

        it("is none for a reader who can't pick versions, with no read") {
            val requests = TestRequestsRepository()
            val vm = sut(requests, authAs(canChoose = false))

            vm.shown()

            vm.uiState.value.needsYou to requests.callsTo("list").size shouldBe (0 to 0)
        }

        it("is none, with no read, when book requests are off") {
            val requests = TestRequestsRepository()
            val vm = sut(requests, authAs(bookRequests = false))

            vm.shown()

            vm.uiState.value.needsYou to requests.callsTo("list").size shouldBe (0 to 0)
        }

        it("keeps the library") {
            val requests = TestRequestsRepository()
            requests.answer("list", picking("a"))
            val active = randomLibrary().copy(isActive = true)
            val vm = sut(requests, libraries = TestLibrariesRepository(listOf(active)))

            vm.shown()

            vm.uiState.value.library shouldBe active
        }
    }
})
