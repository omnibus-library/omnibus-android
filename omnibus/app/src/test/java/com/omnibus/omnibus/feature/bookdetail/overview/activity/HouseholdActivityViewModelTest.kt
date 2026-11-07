package com.omnibus.omnibus.feature.bookdetail.overview.activity

import com.omnibus.omnibus.data.household.HouseholdRepository
import com.omnibus.omnibus.data.household.HouseholdResult
import com.omnibus.omnibus.data.household.TestHouseholdRepository
import com.omnibus.omnibus.data.household.randomHouseholdReader
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdActivityViewModelTest : DescribeSpec({
    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(repository: HouseholdRepository = TestHouseholdRepository()) =
        HouseholdActivityViewModel(randomString(), repository)

    /** A reader who has spent time in the book. */
    fun reader() = randomHouseholdReader().copy(seconds = randomLong(1L..1_000_000L))

    describe("loading the household") {
        it("TC-8: lists the readers the repository returns, in order") {
            val readers = listOf(reader(), reader())
            val repository = TestHouseholdRepository().apply { results += HouseholdResult.Success(readers) }
            val vm = sut(repository)

            vm.reload(0)

            vm.readers.value shouldBe readers
        }

        context("when a reader has no time in the book") {
            it("TC-10: leaves that reader out") {
                val active = reader()
                val idle = reader().copy(seconds = 0)
                val repository = TestHouseholdRepository().apply { results += HouseholdResult.Success(listOf(idle, active)) }
                val vm = sut(repository)

                vm.reload(0)

                vm.readers.value shouldBe listOf(active)
            }
        }

        context("when the same reload key comes again") {
            it("TC-11: does not load again") {
                val repository = TestHouseholdRepository()
                val vm = sut(repository)

                vm.reload(0)
                vm.reload(0)

                repository.calls.size shouldBe 1
            }
        }

        context("when a reload does not get an answer") {
            it("TC-12: keeps the readers already shown, whether the read failed or the book is unknown") {
                checkAll(Exhaustive.of(HouseholdResult.Failed, HouseholdResult.NotFound)) { unanswered ->
                    val shown = listOf(reader())
                    val repository = TestHouseholdRepository().apply {
                        results += HouseholdResult.Success(shown)
                        results += unanswered
                    }
                    val vm = sut(repository)

                    vm.reload(0)
                    vm.reload(1)

                    vm.readers.value shouldBe shown
                }
            }
        }

        context("when a reader has left the list") {
            it("TC-13: removes them on the next reload") {
                val repository = TestHouseholdRepository().apply {
                    results += HouseholdResult.Success(listOf(reader()))
                    results += HouseholdResult.Success(emptyList())
                }
                val vm = sut(repository)

                vm.reload(0)
                vm.reload(1)

                vm.readers.value.shouldBeEmpty()
            }
        }
    }
})
