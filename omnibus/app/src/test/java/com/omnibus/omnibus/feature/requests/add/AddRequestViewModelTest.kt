package com.omnibus.omnibus.feature.requests.add

import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.requests.LibraryHit
import com.omnibus.omnibus.data.requests.MatchedBook
import com.omnibus.omnibus.data.requests.ProviderBook
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.ScanMatch
import com.omnibus.omnibus.data.requests.ScanOutcomeKind
import com.omnibus.omnibus.data.requests.TestRequestsRepository
import com.omnibus.omnibus.data.requests.randomBookRequest
import com.omnibus.omnibus.data.requests.randomLibraryHit
import com.omnibus.omnibus.data.requests.randomProviderBook
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private fun ok(vararg hits: LibraryHit) = RequestsResult.Success(hits.toList())

private fun found(vararg books: ProviderBook) = RequestsResult.Success(books.toList())

private val UNREACHABLE = RequestsResult.Failure(RequestsError.Unreachable)

@OptIn(ExperimentalCoroutinesApi::class)
class AddRequestViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(requests: TestRequestsRepository = TestRequestsRepository(), online: Boolean = true) =
        AddRequestViewModel(requests, TestConnectivityMonitor(online)) { RequestWording() }

    describe("searching") {

        it("waits 250 ms after the last keystroke, then searches the library and the providers") {
            runTest {
                val requests = TestRequestsRepository()
                requests.answer("searchLibrary", ok(randomLibraryHit()))
                requests.answer("searchProviders", found(randomProviderBook()))
                val vm = sut(requests)

                vm.updateQuery("dune")
                advanceTimeBy(249)
                runCurrent()
                val early = requests.calls.size
                advanceTimeBy(1)
                runCurrent()

                early to requests.calls.map { it.function }.toSet() shouldBe (0 to setOf("searchLibrary", "searchProviders"))
            }
        }

        it("restarts the wait on each keystroke, and searches only the last text") {
            runTest {
                val requests = TestRequestsRepository()
                requests.answerAlways("searchLibrary", ok())
                requests.answerAlways("searchProviders", found())
                val vm = sut(requests)

                vm.updateQuery("du")
                advanceTimeBy(200)
                vm.updateQuery("dune")
                advanceTimeBy(300)
                runCurrent()

                requests.callsTo("searchLibrary").map { it.args.single() } shouldBe listOf("dune")
            }
        }

        it("fills each section with its own answer") {
            runTest {
                val hit = randomLibraryHit()
                val book = randomProviderBook()
                val requests = TestRequestsRepository()
                requests.answer("searchLibrary", ok(hit))
                requests.answer("searchProviders", found(book))
                val vm = sut(requests)

                vm.updateQuery("dune")
                advanceTimeBy(250)
                runCurrent()

                vm.uiState.value.let { it.library to it.providers } shouldBe
                    (SearchResults.Done(listOf(hit)) to SearchResults.Done(listOf(book)))
            }
        }

        it("fails one section without the other") {
            runTest {
                val book = randomProviderBook()
                val requests = TestRequestsRepository()
                requests.answer("searchLibrary", UNREACHABLE)
                requests.answer("searchProviders", found(book))
                val vm = sut(requests)

                vm.updateQuery("dune")
                advanceTimeBy(250)
                runCurrent()

                vm.uiState.value.let { it.library to it.providers } shouldBe (SearchResults.Failed to SearchResults.Done(listOf(book)))
            }
        }

        it("answers an empty section as done, which the sheet says as Nothing matches") {
            runTest {
                val requests = TestRequestsRepository()
                requests.answer("searchLibrary", ok())
                requests.answer("searchProviders", found())
                val vm = sut(requests)

                vm.updateQuery("zzz")
                advanceTimeBy(250)
                runCurrent()

                vm.uiState.value.let { it.library to it.providers } shouldBe (SearchResults.Done(emptyList<LibraryHit>()) to SearchResults.Done(emptyList<ProviderBook>()))
            }
        }

        it("drops an older query's answer when a newer one has been searched") {
            runTest {
                val old = randomLibraryHit()
                val fresh = randomLibraryHit()
                val requests = TestRequestsRepository()
                val gate = CompletableDeferred<Unit>()
                requests.answer("searchLibrary", ok(old), gate)
                requests.answer("searchProviders", found())
                val vm = sut(requests)
                vm.updateQuery("dune")
                advanceTimeBy(250)
                runCurrent()
                requests.answer("searchLibrary", ok(fresh))
                requests.answer("searchProviders", found())
                vm.updateQuery("dune messiah")
                advanceTimeBy(250)
                runCurrent()

                gate.complete(Unit)
                runCurrent()

                vm.uiState.value.library shouldBe SearchResults.Done(listOf(fresh))
            }
        }

        it("searches nothing for a blank query") {
            runTest {
                val requests = TestRequestsRepository()
                val vm = sut(requests)

                vm.updateQuery("   ")
                advanceTimeBy(1_000)
                runCurrent()

                requests.calls shouldBe emptyList()
                vm.uiState.value.isIdle shouldBe true
            }
        }
    }

    describe("starting from a library book") {

        it("starts an ebook request on it and asks to open its page") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest().copy(bookUuid = uuid)))
            val vm = sut(requests)

            vm.startFromLibrary(uuid)

            requests.callsTo("start").map { it.args } shouldBe listOf(listOf<Any?>(uuid, RequestKind.Ebook))
            vm.uiState.value.let { it.pendingOpen to it.starting } shouldBe (uuid to false)
        }

        it("says why in the sheet when it fails") {
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Failure(RequestsError.Http(404, null)))
            val vm = sut(requests)

            vm.startFromLibrary(randomString())

            vm.uiState.value.let { it.message to it.pendingOpen } shouldBe ("That book is no longer in the library." to null)
        }

        it("clears the page to open once it has been opened") {
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest()))
            val vm = sut(requests)
            vm.startFromLibrary(randomString())

            vm.opened()

            vm.uiState.value.pendingOpen shouldBe null
        }
    }

    describe("starting from a provider's record") {

        fun match(vararg books: MatchedBook, outcome: ScanOutcomeKind = ScanOutcomeKind.InLibraryUnowned) =
            RequestsResult.Success(ScanMatch(books.toList(), outcome))

        it("resolves the record before it starts anything") {
            val book = randomProviderBook()
            val requests = TestRequestsRepository()
            requests.answer("resolve", match(outcome = ScanOutcomeKind.NotInLibrary))
            requests.answer("startFromRecord", RequestsResult.Success(randomBookRequest()))
            val vm = sut(requests)

            vm.startFromRecord(book, emptySet())

            requests.calls.map { it.function } shouldBe listOf("resolve", "startFromRecord")
        }

        it("starts from the record when the library has no match, and opens the book it made") {
            val made = randomString()
            val requests = TestRequestsRepository()
            requests.answer("resolve", match(outcome = ScanOutcomeKind.NotInLibrary))
            requests.answer("startFromRecord", RequestsResult.Success(randomBookRequest().copy(bookUuid = made)))
            val vm = sut(requests)

            vm.startFromRecord(randomProviderBook(), emptySet())

            vm.uiState.value.pendingOpen shouldBe made
        }

        it("starts from the record, not on a library book, when the match is only a close one") {
            val requests = TestRequestsRepository()
            requests.answer("resolve", match(MatchedBook(randomString(), "Dune", hasFiles = true), outcome = ScanOutcomeKind.CloseMatch))
            requests.answer("startFromRecord", RequestsResult.Success(randomBookRequest()))
            val vm = sut(requests)

            vm.startFromRecord(randomProviderBook().copy(title = "Dune Messiah"), emptySet())

            requests.calls.map { it.function } shouldBe listOf("resolve", "startFromRecord")
        }

        it("starts on the library book it matches when that book has no ebook") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.answer("resolve", match(MatchedBook(uuid, "Dune", hasFiles = false)))
            requests.answer("start", RequestsResult.Success(randomBookRequest().copy(bookUuid = uuid)))
            val vm = sut(requests)

            vm.startFromRecord(randomProviderBook(), emptySet())

            requests.callsTo("start").map { it.args } shouldBe listOf(listOf<Any?>(uuid, RequestKind.Ebook))
        }

        it("opens the request that is already going for the library book it matches") {
            val uuid = randomString()
            val requests = TestRequestsRepository()
            requests.answer("resolve", match(MatchedBook(uuid, "Dune", hasFiles = false)))
            val vm = sut(requests)

            vm.startFromRecord(randomProviderBook(), requested = setOf(uuid))

            vm.uiState.value.pendingOpen to requests.callsTo("start").size shouldBe (uuid to 0)
        }

        it("says the book is already in the library when it holds an ebook, and starts nothing") {
            val requests = TestRequestsRepository()
            requests.answer("resolve", match(MatchedBook(randomString(), "Dune", hasFiles = true)))
            val vm = sut(requests)
            val record = randomProviderBook().copy(title = "Dune")

            vm.startFromRecord(record, emptySet())

            vm.uiState.value.message to requests.callsTo("start").size shouldBe ("Dune is already in the library." to 0)
        }

        it("says it couldn't look the record up when the lookup fails") {
            val requests = TestRequestsRepository()
            requests.answer("resolve", UNREACHABLE)
            val vm = sut(requests)

            vm.startFromRecord(randomProviderBook().copy(title = "Dune"), emptySet())

            vm.uiState.value.message shouldBe "Couldn’t look up Dune."
        }

        it("says why a start from the record failed") {
            val requests = TestRequestsRepository()
            requests.answer("resolve", match(outcome = ScanOutcomeKind.Unresolved))
            requests.answer("startFromRecord", RequestsResult.Failure(RequestsError.Http(429, null)))
            val vm = sut(requests)

            vm.startFromRecord(randomProviderBook(), emptySet())

            vm.uiState.value.message shouldBe "You have a lot of requests going. Try again in a few minutes."
        }
    }

    describe("which result is starting") {
        it("names the library book, or the provider's record, whose request is running") {
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest()), CompletableDeferred())
            val library = sut(requests)
            library.startFromLibrary("u-1")
            val record = randomProviderBook()
            val other = TestRequestsRepository()
            other.answer("resolve", RequestsResult.Success(ScanMatch(emptyList(), ScanOutcomeKind.NotInLibrary)), CompletableDeferred())
            val fromRecord = sut(other)
            fromRecord.startFromRecord(record, emptySet())

            library.uiState.value.startingKey to fromRecord.uiState.value.startingKey shouldBe ("u-1" to record.key())
        }
    }

    describe("a start while another runs") {

        it("is ignored, says so, and leaves every Request button disabled until the first ends") {
            val requests = TestRequestsRepository()
            val gate = CompletableDeferred<Unit>()
            requests.answer("start", RequestsResult.Success(randomBookRequest()), gate)
            val vm = sut(requests)
            vm.startFromLibrary(randomString())
            val whileRunning = vm.uiState.value.starting

            vm.startFromLibrary(randomString())
            val ignored = vm.uiState.value.message
            gate.complete(Unit)

            listOf(whileRunning, requests.callsTo("start").size == 1, ignored, vm.uiState.value.starting) shouldBe
                listOf(true, true, "Still starting the last request.", false)
        }

        it("also holds back a start from a record") {
            val requests = TestRequestsRepository()
            requests.answer("start", RequestsResult.Success(randomBookRequest()), CompletableDeferred())
            val vm = sut(requests)
            vm.startFromLibrary(randomString())

            vm.startFromRecord(randomProviderBook(), emptySet())

            requests.callsTo("resolve") shouldBe emptyList()
        }
    }

    describe("while the device is offline") {
        it("starts nothing") {
            val requests = TestRequestsRepository()
            val vm = sut(requests, online = false)

            vm.startFromLibrary(randomString())

            requests.callsTo("start") shouldBe emptyList()
        }
    }
})
