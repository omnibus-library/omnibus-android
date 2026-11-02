package com.omnibus.omnibus.feature.bookdetail.overview.author

import com.omnibus.omnibus.data.authors.Author
import com.omnibus.omnibus.data.authors.AuthorResult
import com.omnibus.omnibus.data.authors.AuthorsError
import com.omnibus.omnibus.data.authors.FakeAuthorsRepository
import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MoreByAuthorViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun author(count: Int) = AuthorResult.Success(
        Author(7, "A", (1..count).map { testBook(it.toLong()) }),
    )

    fun viewModel(fake: FakeAuthorsRepository, bookId: Long = 1) = MoreByAuthorViewModel(7, bookId, fake)

    describe("reload") {

        it("#105 TC-18: starts empty, before anything is read") {
            viewModel(FakeAuthorsRepository(author(3))).uiState.value.books.shouldBeEmpty()
        }

        it("#105 TC-19: reads the author and shows their other books") {
            val fake = FakeAuthorsRepository(author(3))
            val vm = viewModel(fake, bookId = 1)

            vm.reload(0)

            fake.requested shouldContainExactly listOf(7L)
            vm.uiState.value.books.map { it.uuid } shouldContainExactly listOf("uuid-2", "uuid-3")
        }

        it("#105 TC-20: limits the shelf to 12 books") {
            val vm = viewModel(FakeAuthorsRepository(author(40)), bookId = 1)

            vm.reload(0)

            vm.uiState.value.books.size shouldBe 12
        }

        it("#105 TC-21: an author with no other books gives an empty shelf") {
            val vm = viewModel(FakeAuthorsRepository(author(1)), bookId = 1)

            vm.reload(0)

            vm.uiState.value.books.shouldBeEmpty()
        }

        it("#105 TC-22: the same reload key reads only once") {
            val fake = FakeAuthorsRepository(author(3))
            val vm = viewModel(fake)

            vm.reload(0)
            vm.reload(0)

            fake.requested.size shouldBe 1
        }

        it("#105 TC-23: a new reload key reads again and replaces the shelf") {
            val fake = FakeAuthorsRepository(author(3))
            val vm = viewModel(fake, bookId = 1)
            vm.reload(0)

            fake.result = author(4)
            vm.reload(1)

            fake.requested.size shouldBe 2
            vm.uiState.value.books.size shouldBe 3
        }

        it("#105 TC-24: a failed read leaves an empty shelf empty") {
            val vm = viewModel(FakeAuthorsRepository(AuthorResult.Failure(AuthorsError.Unreachable)))

            vm.reload(0)

            vm.uiState.value.books.shouldBeEmpty()
        }

        it("#105 TC-25: a failed refresh keeps the books already shown") {
            val fake = FakeAuthorsRepository(author(3))
            val vm = viewModel(fake, bookId = 1)
            vm.reload(0)

            fake.result = AuthorResult.Failure(AuthorsError.Unreachable)
            vm.reload(1)

            vm.uiState.value.books.map { it.uuid } shouldContainExactly listOf("uuid-2", "uuid-3")
        }

        it("#105 TC-26: no saved server leaves the shelf empty") {
            val vm = viewModel(FakeAuthorsRepository(AuthorResult.NoServer))

            vm.reload(0)

            vm.uiState.value.books.shouldBeEmpty()
        }
    }
})
