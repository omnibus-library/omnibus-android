package com.omnibus.omnibus.feature.bookdetail.overview.author

import com.omnibus.omnibus.data.authors.Author
import com.omnibus.omnibus.data.authors.AuthorResult
import com.omnibus.omnibus.data.authors.AuthorsError
import com.omnibus.omnibus.data.authors.AuthorsRepository
import com.omnibus.omnibus.data.authors.TestAuthorsRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
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
class MoreByAuthorViewModelTest : DescribeSpec({
    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        repository: AuthorsRepository = TestAuthorsRepository(),
        authorId: Long = randomLong(),
        bookId: Long = randomLong(),
    ) = MoreByAuthorViewModel(authorId, bookId, repository)

    /** An author whose books have the ids 1 to [count]. */
    fun author(count: Int) = AuthorResult.Success(
        Author(randomLong(), randomString(), (1..count).map { randomBook().copy(id = it.toLong()) }),
    )

    fun AuthorResult.Success.uuids() = author.books.map { it.uuid }

    describe("loading the shelf") {
        it("#105 TC-19: reads the author it was made for") {
            val authorId = randomLong()
            val repository = TestAuthorsRepository()

            sut(repository, authorId = authorId).reload(0)

            repository.requested shouldContainExactly listOf(authorId)
        }

        it("#105 TC-19: shows the author's books other than this one") {
            val author = author(3)
            val vm = sut(TestAuthorsRepository(author), bookId = 1)

            vm.reload(0)

            vm.uiState.value.books.map { it.uuid } shouldContainExactly author.uuids().drop(1)
        }

        context("when the same reload key comes again") {
            it("#105 TC-22: does not read again") {
                val repository = TestAuthorsRepository(author(3))
                val vm = sut(repository)

                vm.reload(0)
                vm.reload(0)

                repository.requested.size shouldBe 1
            }
        }

        context("when a new reload key comes") {
            it("#105 TC-23: replaces the shelf with the new answer") {
                val repository = TestAuthorsRepository(author(3))
                val vm = sut(repository, bookId = 1)
                val next = author(4)

                vm.reload(0)
                repository.result = next
                vm.reload(1)

                vm.uiState.value.books.map { it.uuid } shouldContainExactly next.uuids().drop(1)
            }
        }

        context("when a refresh fails") {
            it("#105 TC-25: keeps the books already shown, for a failed read or no saved server") {
                checkAll(Exhaustive.of(AuthorResult.Failure(AuthorsError.Unreachable), AuthorResult.NoServer)) { failed ->
                    val author = author(3)
                    val repository = TestAuthorsRepository(author)
                    val vm = sut(repository, bookId = 1)

                    vm.reload(0)
                    repository.result = failed
                    vm.reload(1)

                    vm.uiState.value.books.map { it.uuid } shouldContainExactly author.uuids().drop(1)
                }
            }
        }
    }
})
