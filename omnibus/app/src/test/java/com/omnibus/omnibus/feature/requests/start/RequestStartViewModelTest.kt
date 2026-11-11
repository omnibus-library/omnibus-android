package com.omnibus.omnibus.feature.requests.start

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.TestRequestsRepository
import com.omnibus.omnibus.data.requests.randomBookRequest
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun authAs(bookRequests: Boolean = true, canStart: Boolean = true) = TestAuthRepository().apply {
    currentUserState.value = randomCurrentUser().copy(bookRequests = bookRequests, canStartRequests = canStart)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RequestStartViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        uuid: String = randomString(),
        requests: TestRequestsRepository = TestRequestsRepository(),
        auth: TestAuthRepository = authAs(),
        online: Boolean = true,
    ) = RequestStartViewModel(uuid, requests, auth, TestConnectivityMonitor(online)) { RequestWording() }

    describe("who is offered the buttons") {
        it("is a reader with book requests on who may start one") {
            listOf(
                sut(auth = authAs(true, true)).uiState.value.canStart,
                sut(auth = authAs(false, true)).uiState.value.canStart,
                sut(auth = authAs(true, false)).uiState.value.canStart,
            ) shouldBe listOf(true, false, false)
        }
    }

    describe("starting a request") {

        it("sends the book and the kind, and shows which button is busy") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest()), CompletableDeferred())
            val vm = sut(uuid, requests)

            vm.start(RequestKind.Audiobook)

            requests.callsTo("start").map { it.args } shouldBe listOf(listOf<Any?>(uuid, RequestKind.Audiobook))
            vm.uiState.value.starting shouldBe RequestKind.Audiobook
        }

        it("asks to open the request page once, whether the server answered 200 or 201") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest().copy(bookUuid = uuid)))
            val vm = sut(uuid, requests)

            vm.start(RequestKind.Ebook)

            vm.uiState.value.let { it.pendingOpen to it.starting } shouldBe (uuid to null)
        }

        it("stops asking once the page has been opened") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest().copy(bookUuid = uuid)))
            val vm = sut(uuid, requests)
            vm.start(RequestKind.Ebook)

            vm.opened()

            vm.uiState.value.pendingOpen shouldBe null
        }

        it("ignores a second start while one runs") {
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest()), CompletableDeferred())
            val vm = sut(requests = requests)
            vm.start(RequestKind.Ebook)

            vm.start(RequestKind.Audiobook)

            requests.callsTo("start").size shouldBe 1
        }

        it("sends nothing while the device is offline") {
            val requests = TestRequestsRepository()
            val vm = sut(requests = requests, online = false)

            vm.start(RequestKind.Ebook)

            requests.callsTo("start") shouldBe emptyList()
        }

        it("sends nothing for a reader who can't start requests") {
            val requests = TestRequestsRepository()
            val vm = sut(requests = requests, auth = authAs(canStart = false))

            vm.start(RequestKind.Ebook)

            requests.callsTo("start") shouldBe emptyList()
        }

        context("when the start fails") {
            it("says why, opens nothing and frees the buttons") {
                val requests = TestRequestsRepository()
                requests.answer("start", RequestsResult.Failure(RequestsError.Http(403, null)))
                val vm = sut(requests = requests)

                vm.start(RequestKind.Ebook)

                vm.uiState.value.let { Triple(it.message?.text, it.pendingOpen, it.starting) } shouldBe
                    Triple("Your account can’t make requests.", null, null)
            }

            it("clears the message once it has been shown") {
                val requests = TestRequestsRepository()
                requests.answer("start", RequestsResult.Failure(RequestsError.Unreachable))
                val vm = sut(requests = requests)
                vm.start(RequestKind.Ebook)

                vm.messageShown(vm.uiState.value.message!!)

                vm.uiState.value.message shouldBe null
            }
        }
    }
})
