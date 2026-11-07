package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.data.series.SeriesError
import com.omnibus.omnibus.data.series.SeriesRepository
import com.omnibus.omnibus.data.series.SeriesResult
import com.omnibus.omnibus.data.series.TestSeriesRepository
import com.omnibus.omnibus.data.series.randomSeriesMember
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val failures = Exhaustive.of(SeriesResult.NoServer, SeriesResult.Failure(SeriesError.Unreachable))

private fun success(count: Int = 2) = SeriesResult.Success(List(count) { randomSeriesMember() })

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest : DescribeSpec({
    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(repository: SeriesRepository = TestSeriesRepository()) = SeriesViewModel(randomLong(), repository)

    describe("loading the series") {
        it("#104 TC-53: keeps the members in the server's order") {
            val result = success(3)
            val repository = TestSeriesRepository().apply { results.addLast(result) }
            val vm = sut(repository)

            vm.reload(0)

            vm.uiState.value.members shouldBe result.members
        }

        context("when the same reload key comes again") {
            it("#104 TC-54: does not fetch again") {
                val repository = TestSeriesRepository()
                val vm = sut(repository)

                vm.reload(0)
                vm.reload(0)

                repository.calls.size shouldBe 1
            }
        }

        context("when the first load fails") {
            it("#104 TC-56: shows nothing, for a failed read or no saved server") {
                checkAll(failures) { failure ->
                    val repository = TestSeriesRepository().apply { results.addLast(failure) }
                    val vm = sut(repository)

                    vm.reload(0)

                    vm.uiState.value.members.shouldBeNull()
                }
            }
        }

        context("when a refresh fails") {
            it("#104 TC-57: keeps the last good members, for a failed read or no saved server") {
                checkAll(failures) { failure ->
                    val result = success()
                    val repository = TestSeriesRepository().apply {
                        results.addLast(result)
                        results.addLast(failure)
                    }
                    val vm = sut(repository)

                    vm.reload(0)
                    vm.reload(1)

                    vm.uiState.value.members shouldBe result.members
                }
            }
        }

        context("when a refresh succeeds") {
            it("#104 TC-58: replaces the members") {
                val next = success()
                val repository = TestSeriesRepository().apply {
                    results.addLast(success())
                    results.addLast(next)
                }
                val vm = sut(repository)

                vm.reload(0)
                vm.reload(1)

                vm.uiState.value.members shouldBe next.members
            }
        }

        context("when a newer reload supersedes one in flight") {
            it("#104 TC-59: ignores the older answer") {
                val fresh = success()
                val gate = CompletableDeferred<Unit>()
                val repository = TestSeriesRepository().apply {
                    results.addLast(success())
                    results.addLast(fresh)
                    gates.addLast(gate)
                }
                val vm = sut(repository)

                vm.reload(0)
                vm.reload(1)
                gate.complete(Unit)

                vm.uiState.value.members shouldBe fresh.members
            }
        }
    }
})
