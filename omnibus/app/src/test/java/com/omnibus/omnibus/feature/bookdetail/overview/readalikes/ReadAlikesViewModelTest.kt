package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import com.omnibus.omnibus.data.suggestions.FakeSuggestionsRepository
import com.omnibus.omnibus.data.suggestions.SuggestionsAnswer
import com.omnibus.omnibus.data.suggestions.SuggestionsError
import com.omnibus.omnibus.data.suggestions.SuggestionsResult
import com.omnibus.omnibus.data.suggestions.pending
import com.omnibus.omnibus.data.suggestions.ready
import com.omnibus.omnibus.data.suggestions.suggestion
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReadAlikesViewModelTest : DescribeSpec({

    val unreachable = SuggestionsResult.Failure(SuggestionsError.Unreachable)
    val notConfigured = SuggestionsResult.Success(SuggestionsAnswer.NotConfigured)
    val unavailable = SuggestionsResult.Success(SuggestionsAnswer.Unavailable)

    /** A fake whose reads are stamped with the test clock. */
    fun TestScope.timed(vararg answers: SuggestionsResult) =
        FakeSuggestionsRepository(*answers).also { it.now = { testScheduler.currentTime } }

    describe("ReadAlikesViewModel") {

        it("#106 TC-30: the state starts Hidden, before anything is read") {
            runTest {
                val fake = timed(ready(suggestion(1)))
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                fake.requests shouldBe 0
            }
        }

        it("#106 TC-32: a ready answer gives Ready in server order and stops") {
            runTest {
                val fake = timed(ready(suggestion(3), suggestion(1), suggestion(2)))
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)

                val shelf = vm.uiState.value.shouldBeInstanceOf<ReadAlikesUiState.Ready>()
                shelf.items.map { it.hardcoverId } shouldContainExactly listOf(3L, 1L, 2L)
                advanceTimeBy(60_000)
                runCurrent()
                fake.requests shouldBe 1
            }
        }

        it("#106 TC-33: a ready answer with no items gives Hidden") {
            runTest {
                val fake = timed(ready())
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)

                vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                fake.requests shouldBe 1
            }
        }

        it("#106 TC-34: unsafe suggestions are dropped, and none left gives Hidden") {
            runTest {
                val fake = timed(
                    ready(
                        suggestion(1, url = "javascript:alert(1)"),
                        suggestion(2, url = "https://example.com/b"),
                    ),
                )
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)

                vm.uiState.value shouldBe ReadAlikesUiState.Hidden
            }
        }

        it("#106 TC-34: with one safe and one unsafe suggestion, Ready shows only the safe one") {
            runTest {
                val fake = timed(ready(suggestion(1, url = "javascript:alert(1)"), suggestion(2)))
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)

                vm.uiState.value.shouldBeInstanceOf<ReadAlikesUiState.Ready>()
                    .items.map { it.hardcoverId } shouldContainExactly listOf(2L)
            }
        }

        it("#106 TC-35: not_configured gives Hidden and does not poll") {
            runTest {
                val fake = timed(notConfigured)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                advanceTimeBy(60_000)

                vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                fake.requests shouldBe 1
            }
        }

        it("#106 TC-36: unavailable gives Unavailable and does not poll") {
            runTest {
                val fake = timed(unavailable)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                advanceTimeBy(60_000)

                vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
                fake.requests shouldBe 1
            }
        }

        it("#106 TC-37: pending gives Searching and asks again at exactly 4 000 ms") {
            runTest {
                val fake = timed(pending)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                launch { vm.load(0) }
                runCurrent()
                vm.uiState.value shouldBe ReadAlikesUiState.Searching
                fake.requests shouldBe 1

                advanceTimeBy(3_999)
                runCurrent()
                fake.requests shouldBe 1

                advanceTimeBy(1)
                runCurrent()
                fake.requests shouldBe 2
            }
        }

        it("#106 TC-38: pending then ready shows the shelf and stops polling") {
            runTest {
                val fake = timed(pending, ready(suggestion(1)))
                val vm = ReadAlikesViewModel("uuid-1", fake)

                launch { vm.load(0) }
                runCurrent()
                vm.uiState.value shouldBe ReadAlikesUiState.Searching

                advanceTimeBy(4_000)
                runCurrent()
                vm.uiState.value.shouldBeInstanceOf<ReadAlikesUiState.Ready>()
                fake.requests shouldBe 2

                advanceTimeBy(60_000)
                runCurrent()
                fake.requests shouldBe 2
            }
        }

        it("#106 TC-39: always pending makes 6 requests over 20 s, then shows Unavailable and stops") {
            runTest {
                val fake = timed(pending)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                launch { vm.load(0) }
                runCurrent()
                advanceTimeBy(19_999)
                runCurrent()
                vm.uiState.value shouldBe ReadAlikesUiState.Searching

                advanceTimeBy(1)
                runCurrent()
                fake.requestTimes shouldContainExactly listOf(0L, 4_000L, 8_000L, 12_000L, 16_000L, 20_000L)
                vm.uiState.value shouldBe ReadAlikesUiState.Unavailable

                advanceTimeBy(60_000)
                runCurrent()
                fake.requests shouldBe 6
            }
        }

        listOf(
            "a failed read" to unreachable,
            "no saved server" to SuggestionsResult.NoServer,
        ).forEach { (name, middle) ->
            it("#106 TC-40: $name in the middle of the poll keeps Searching and the next re-ask goes on") {
                runTest {
                    val fake = timed(pending, middle, ready(suggestion(1)))
                    val vm = ReadAlikesViewModel("uuid-1", fake)

                    launch { vm.load(0) }
                    runCurrent()
                    advanceTimeBy(4_000)
                    runCurrent()
                    vm.uiState.value shouldBe ReadAlikesUiState.Searching

                    advanceTimeBy(4_000)
                    runCurrent()
                    vm.uiState.value.shouldBeInstanceOf<ReadAlikesUiState.Ready>()
                    fake.requests shouldBe 3
                }
            }
        }

        it("#106 TC-41: failed re-asks count against the budget and end in Unavailable") {
            runTest {
                val fake = timed(pending, unreachable)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                launch { vm.load(0) }
                runCurrent()
                advanceTimeBy(19_999)
                runCurrent()
                vm.uiState.value shouldBe ReadAlikesUiState.Searching

                advanceTimeBy(1)
                runCurrent()
                fake.requests shouldBe 6
                vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
            }
        }

        listOf(
            "NoServer" to SuggestionsResult.NoServer,
            "an unreachable server" to unreachable,
            "a body that does not decode" to SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse),
            "a rejected session" to SuggestionsResult.Failure(SuggestionsError.SessionExpired),
            "HTTP 500" to SuggestionsResult.Failure(SuggestionsError.Http(500)),
        ).forEach { (name, result) ->
            it("#106 TC-42: a failed first read ($name) gives Hidden and does not poll") {
                runTest {
                    val fake = timed(result)
                    val vm = ReadAlikesViewModel("uuid-1", fake)

                    vm.load(0)
                    advanceTimeBy(60_000)

                    vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                    fake.requests shouldBe 1
                }
            }
        }

        it("#106 TC-43: a failed refresh keeps the shelf") {
            runTest {
                val fake = timed(ready(suggestion(1), suggestion(2)), unreachable)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                val before = vm.uiState.value
                vm.load(1)

                vm.uiState.value shouldBe before
                before.shouldBeInstanceOf<ReadAlikesUiState.Ready>()
                fake.requests shouldBe 2
            }
        }

        it("#106 TC-44: a failed refresh keeps Unavailable") {
            runTest {
                val fake = timed(unavailable, unreachable)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                vm.load(1)

                vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
            }
        }

        it("#106 TC-45: a new not_configured from the server hides a shelf") {
            runTest {
                val fake = timed(ready(suggestion(1)), notConfigured)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                vm.load(1)

                vm.uiState.value shouldBe ReadAlikesUiState.Hidden
            }
        }

        it("#106 TC-45: a new unavailable from the server replaces a shelf") {
            runTest {
                val fake = timed(ready(suggestion(1)), unavailable)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                vm.load(1)

                vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
            }
        }

        it("#106 TC-46: the same reload key after a finished load makes no request") {
            runTest {
                val fake = timed(ready(suggestion(1)))
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                vm.load(0)

                fake.requests shouldBe 1
            }
        }

        it("#106 TC-47: a new reload key reads again with a fresh poll budget") {
            runTest {
                val fake = timed(pending)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                vm.load(0)
                fake.requests shouldBe 6

                vm.load(1)

                fake.requests shouldBe 12
                fake.requestTimes.drop(6) shouldContainExactly
                    listOf(20_000L, 24_000L, 28_000L, 32_000L, 36_000L, 40_000L)
                vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
            }
        }

        it("#106 TC-48: cancelling the load mid-poll stops all requests and keeps Searching") {
            runTest {
                val fake = timed(pending)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                val job = launch { vm.load(0) }
                runCurrent()
                advanceTimeBy(5_000)
                runCurrent()
                fake.requests shouldBe 2

                job.cancel()
                advanceTimeBy(60_000)
                runCurrent()

                fake.requests shouldBe 2
                vm.uiState.value shouldBe ReadAlikesUiState.Searching
            }
        }

        it("#106 TC-49: an interrupted load for a key reads again when called again") {
            runTest {
                val fake = timed(pending)
                val vm = ReadAlikesViewModel("uuid-1", fake)

                val job = launch { vm.load(0) }
                runCurrent()
                advanceTimeBy(5_000)
                runCurrent()
                job.cancel()

                launch { vm.load(0) }
                advanceTimeBy(20_000)
                runCurrent()

                // Two requests before the cancel, then a fresh budget of one read plus five re-asks.
                fake.requests shouldBe 8
                vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
            }
        }

        it("#106 TC-50: cancelling during a read leaves the state and makes no further request") {
            runTest {
                val fake = timed(pending)
                fake.gate = CompletableDeferred<Unit>()
                val vm = ReadAlikesViewModel("uuid-1", fake)

                val job = launch { vm.load(0) }
                runCurrent()
                job.cancel()
                fake.gate!!.complete(Unit)
                runCurrent()

                vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                fake.requests shouldBe 1
                job.isCancelled shouldBe true
            }
        }
    }
})
