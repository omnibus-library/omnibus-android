package com.omnibus.omnibus.feature.bookdetail.overview.activity

import com.omnibus.omnibus.data.household.FakeHouseholdRepository
import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.data.household.HouseholdResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdActivityViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun reader(id: Long, seconds: Long = 3_600L) =
        HouseholdReader(id, "Reader $id", hasAvatar = false, seconds = seconds, sessions = 2, lastSessionAtMillis = 1_000L)

    describe("loading the household") {

        it("TC-7: starts with no readers, so the section is left out") {
            HouseholdActivityViewModel("book-1", FakeHouseholdRepository()).readers.value shouldBe emptyList()
        }

        it("TC-8: lists the readers the repository returns, in order") {
            val fake = FakeHouseholdRepository()
            fake.results += HouseholdResult.Success(listOf(reader(2), reader(1)))
            val vm = HouseholdActivityViewModel("book-1", fake)

            vm.reload(0)

            vm.readers.value shouldBe listOf(reader(2), reader(1))
            fake.calls shouldBe listOf("book-1")
        }

        it("TC-9: a book nobody else has read shows no readers") {
            val fake = FakeHouseholdRepository()
            fake.results += HouseholdResult.Success(emptyList())
            val vm = HouseholdActivityViewModel("book-1", fake)

            vm.reload(0)

            vm.readers.value shouldBe emptyList()
        }

        it("TC-10: a reader with no time in the book is not listed") {
            val fake = FakeHouseholdRepository()
            fake.results += HouseholdResult.Success(listOf(reader(1, seconds = 0), reader(2)))
            val vm = HouseholdActivityViewModel("book-1", fake)

            vm.reload(0)

            vm.readers.value shouldBe listOf(reader(2))
        }

        it("TC-11: loads once per reload key") {
            val fake = FakeHouseholdRepository()
            val vm = HouseholdActivityViewModel("book-1", fake)

            vm.reload(0)
            vm.reload(0)
            vm.reload(1)

            fake.calls shouldBe listOf("book-1", "book-1")
        }

        it("TC-12: a failed or unknown-book answer keeps the readers already shown") {
            val fake = FakeHouseholdRepository()
            fake.results += HouseholdResult.Success(listOf(reader(1)))
            fake.results += HouseholdResult.Failed
            fake.results += HouseholdResult.NotFound
            val vm = HouseholdActivityViewModel("book-1", fake)

            vm.reload(0)
            vm.reload(1)
            vm.reload(2)

            vm.readers.value shouldBe listOf(reader(1))
        }

        it("TC-13: a reload replaces the list, so a reader who left it disappears") {
            val fake = FakeHouseholdRepository()
            fake.results += HouseholdResult.Success(listOf(reader(1)))
            fake.results += HouseholdResult.Success(emptyList())
            val vm = HouseholdActivityViewModel("book-1", fake)

            vm.reload(0)
            vm.reload(1)

            vm.readers.value shouldBe emptyList()
        }
    }
})
