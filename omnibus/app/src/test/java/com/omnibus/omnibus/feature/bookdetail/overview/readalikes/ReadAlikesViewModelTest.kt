package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import com.omnibus.omnibus.data.suggestions.SuggestionsAnswer
import com.omnibus.omnibus.data.suggestions.SuggestionsError
import com.omnibus.omnibus.data.suggestions.SuggestionsRepository
import com.omnibus.omnibus.data.suggestions.SuggestionsResult
import com.omnibus.omnibus.data.suggestions.TestSuggestionsRepository
import com.omnibus.omnibus.data.suggestions.pending
import com.omnibus.omnibus.data.suggestions.randomBookSuggestion
import com.omnibus.omnibus.data.suggestions.ready
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val unreachable = SuggestionsResult.Failure(SuggestionsError.Unreachable)
private val notConfigured = SuggestionsResult.Success(SuggestionsAnswer.NotConfigured)
private val unavailable = SuggestionsResult.Success(SuggestionsAnswer.Unavailable)

private fun readyShelf() = ready(randomBookSuggestion())

@OptIn(ExperimentalCoroutinesApi::class)
class ReadAlikesViewModelTest : DescribeSpec({
    fun sut(repository: SuggestionsRepository = TestSuggestionsRepository()) =
        ReadAlikesViewModel(randomString(), repository)

    /** A fake whose reads are stamped with the test clock. */
    fun TestScope.timed(vararg answers: SuggestionsResult) =
        TestSuggestionsRepository(*answers).also { it.now = { testScheduler.currentTime } }

    /** A view model that has finished its first load, against these [answers]. */
    suspend fun TestScope.loaded(vararg answers: SuggestionsResult) = sut(timed(*answers)).also { it.load(0) }

    /** Moves the test clock on by [millis], and runs what is then due. */
    fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    describe("loading the suggestions") {
        context("when the server is ready") {
            it("#106 TC-32: shows the shelf without asking again") {
                runTest {
                    val fake = timed(readyShelf())
                    val vm = sut(fake)

                    vm.load(0)
                    advance(60_000)

                    vm.uiState.value.shouldBeInstanceOf<ReadAlikesUiState.Ready>()
                    fake.requests shouldBe 1
                }
            }
        }

        context("when no suggestion is safe to show") {
            it("#106 TC-34: hides the section") {
                runTest {
                    val vm = loaded(ready(randomBookSuggestion().copy(hardcoverUrl = "https://example.com/b")))



                    vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                }
            }
        }

        context("when the server is not configured") {
            it("#106 TC-35: hides the section") {
                runTest {
                    val vm = loaded(notConfigured)


                    vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                }
            }
        }

        context("when the server has given up") {
            it("#106 TC-36: shows the section as unavailable") {
                runTest {
                    val vm = loaded(unavailable)


                    vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
                }
            }
        }

        context("when the first read fails") {
            it("#106 TC-42: hides the section without asking again, for a failed read or no saved server") {
                checkAll(Exhaustive.of(unreachable, SuggestionsResult.NoServer)) { failed ->
                    runTest {
                        val fake = timed(failed)
                        val vm = sut(fake)

                        vm.load(0)

                        vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                        fake.requests shouldBe 1
                    }
                }
            }
        }
    }

    describe("polling") {
        context("while the server searches") {
            it("#106 TC-37: shows the section as searching") {
                runTest {
                    val vm = sut(timed(pending))

                    val job = launch { vm.load(0) }
                    runCurrent()

                    vm.uiState.value shouldBe ReadAlikesUiState.Searching
                    job.cancel()
                }
            }

            context("when the server keeps searching") {
                it("#106 TC-39: asks every 4 s for 20 s, then shows the section as unavailable") {
                    runTest {
                        val fake = timed(pending)
                        val vm = sut(fake)

                        vm.load(0)
                        advance(60_000)

                        fake.requestTimes shouldContainExactly listOf(0L, 4_000L, 8_000L, 12_000L, 16_000L, 20_000L)
                        vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
                    }
                }
            }

            context("when a re-ask fails") {
                it("#106 TC-40: keeps asking, for a failed read or no saved server") {
                    checkAll(Exhaustive.of(unreachable, SuggestionsResult.NoServer)) { failed ->
                        runTest {
                            val vm = loaded(pending, failed, readyShelf())


                            vm.uiState.value.shouldBeInstanceOf<ReadAlikesUiState.Ready>()
                        }
                    }
                }
            }

            context("when every re-ask fails") {
                it("#106 TC-41: spends the budget, then shows the section as unavailable") {
                    runTest {
                        val fake = timed(pending, unreachable)
                        val vm = sut(fake)

                        vm.load(0)

                        fake.requests shouldBe 6
                        vm.uiState.value shouldBe ReadAlikesUiState.Unavailable
                    }
                }
            }

            context("when the load is cancelled") {
                it("#106 TC-48: stops asking") {
                    runTest {
                        val fake = timed(pending)
                        val vm = sut(fake)

                        val job = launch { vm.load(0) }
                        advance(5_000)
                        job.cancel()
                        advance(60_000)

                        fake.requests shouldBe 2
                    }
                }

                context("when a later load has the same key") {
                    it("#106 TC-49: reads again with a fresh budget") {
                        runTest {
                            val fake = timed(pending)
                            val vm = sut(fake)

                            val job = launch { vm.load(0) }
                            advance(5_000)
                            job.cancel()
                            vm.load(0)

                            // Two requests before the cancel, then one read plus five re-asks.
                            fake.requests shouldBe 8
                        }
                    }
                }
            }
        }
    }

    describe("refreshing the suggestions") {
        context("when the read fails") {
            it("#106 TC-43: keeps the shelf") {
                runTest {
                    val vm = sut(timed(readyShelf(), unreachable))
                    vm.load(0)
                    val shelf = vm.uiState.value.shouldBeInstanceOf<ReadAlikesUiState.Ready>()

                    vm.load(1)

                    vm.uiState.value shouldBe shelf
                }
            }
        }

        context("when the server's status has changed") {
            it("#106 TC-45: follows the server") {
                runTest {
                    val vm = loaded(readyShelf(), notConfigured)
                    vm.load(1)

                    vm.uiState.value shouldBe ReadAlikesUiState.Hidden
                }
            }
        }

        context("when the same reload key comes again after a finished load") {
            it("#106 TC-46: makes no request") {
                runTest {
                    val fake = timed(readyShelf())
                    val vm = sut(fake)

                    vm.load(0)
                    vm.load(0)

                    fake.requests shouldBe 1
                }
            }
        }
    }
})
