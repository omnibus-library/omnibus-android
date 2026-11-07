package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.data.series.TestSeriesRepository
import com.omnibus.omnibus.data.series.SeriesError
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.data.series.SeriesResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun member(uuid: String) = SeriesMember(uuid, "Title $uuid", null, null, null, false)
    fun success(vararg uuids: String) = SeriesResult.Success(uuids.map { member(it) })

    describe("loading") {

        it("#104 TC-52: it does nothing before reload") {
            val fake = TestSeriesRepository()
            val vm = SeriesViewModel(4L, fake)

            vm.uiState.value.members shouldBe null
            fake.calls.isEmpty() shouldBe true
        }

        it("#104 TC-53: reload fetches the series and keeps the members in order") {
            val fake = TestSeriesRepository()
            fake.results.addLast(success("a", "b", "c"))
            val vm = SeriesViewModel(4L, fake)

            vm.reload(0)

            fake.calls shouldContainExactly listOf(4L)
            vm.uiState.value.members shouldBe listOf(member("a"), member("b"), member("c"))
        }

        it("#104 TC-54: the same key fetches once") {
            val fake = TestSeriesRepository()
            fake.results.addLast(success("a"))
            val vm = SeriesViewModel(4L, fake)

            vm.reload(0)
            vm.reload(0)

            fake.calls.size shouldBe 1
        }

        it("#104 TC-55: a new reload key fetches again, and the same key still does not") {
            val fake = TestSeriesRepository()
            fake.results.addLast(success("a"))
            fake.results.addLast(success("b"))
            val vm = SeriesViewModel(4L, fake)

            vm.reload(0)
            vm.reload(1)
            vm.reload(1)

            fake.calls.size shouldBe 2
            vm.uiState.value.members shouldBe listOf(member("b"))
        }

        it("#104 TC-56: a failed first load shows nothing") {
            val failures = listOf(
                SeriesResult.NoServer,
                SeriesResult.Failure(SeriesError.Unreachable),
                SeriesResult.Failure(SeriesError.UnexpectedResponse),
                SeriesResult.Failure(SeriesError.SessionExpired),
                SeriesResult.Failure(SeriesError.NotFound),
                SeriesResult.Failure(SeriesError.Http(500)),
            )
            for (failure in failures) {
                val fake = TestSeriesRepository()
                fake.results.addLast(failure)
                val vm = SeriesViewModel(4L, fake)

                vm.reload(0)

                vm.uiState.value.members shouldBe null
            }
        }

        it("#104 TC-57: a failed refresh keeps the last good members") {
            for (failure in listOf(SeriesResult.NoServer, SeriesResult.Failure(SeriesError.Unreachable))) {
                val fake = TestSeriesRepository()
                fake.results.addLast(success("a", "b"))
                fake.results.addLast(failure)
                val vm = SeriesViewModel(4L, fake)

                vm.reload(0)
                vm.reload(1)

                vm.uiState.value.members shouldBe listOf(member("a"), member("b"))
            }
        }

        it("#104 TC-58: a refresh replaces the members") {
            val fake = TestSeriesRepository()
            fake.results.addLast(success("a"))
            fake.results.addLast(success("b", "c"))
            val vm = SeriesViewModel(4L, fake)

            vm.reload(0)
            vm.reload(1)

            vm.uiState.value.members shouldBe listOf(member("b"), member("c"))
        }

        it("#104 TC-59: a newer reload supersedes one in flight") {
            val fake = TestSeriesRepository()
            val gate = CompletableDeferred<Unit>()
            fake.results.addLast(success("stale"))
            fake.results.addLast(success("fresh"))
            fake.gates.addLast(gate)
            val vm = SeriesViewModel(4L, fake)

            vm.reload(0)
            vm.reload(1)
            vm.uiState.value.members shouldBe listOf(member("fresh"))

            gate.complete(Unit)
            vm.uiState.value.members shouldBe listOf(member("fresh"))
        }

        it("#104 TC-60: a failed key is not retried, and a new key retries") {
            val fake = TestSeriesRepository()
            fake.results.addLast(SeriesResult.Failure(SeriesError.Unreachable))
            fake.results.addLast(success("a"))
            val vm = SeriesViewModel(4L, fake)

            vm.reload(0)
            vm.reload(0)
            fake.calls.size shouldBe 1

            vm.reload(1)
            fake.calls.size shouldBe 2
            vm.uiState.value.members shouldBe listOf(member("a"))
        }

        it("#104 TC-61: it fetches the id it was created with") {
            val fake = TestSeriesRepository()
            val four = SeriesViewModel(4L, fake)
            val nine = SeriesViewModel(9L, fake)

            four.reload(0)
            nine.reload(0)

            fake.calls shouldContainExactly listOf(4L, 9L)
        }
    }
})
