package com.omnibus.omnibus.feature.search.facet

import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.BooksFacet
import com.omnibus.omnibus.data.library.BooksPage
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.FacetKind
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

class FacetGenreBooksTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(StandardTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    describe("opening the Books in genre list") {

        it("#353 TC-8: asks for the first page once, by title, filtered to the genre") {
            val name = randomString()
            val repo = TestLibraryRepository().apply {
                enqueue(LibraryPageResult.Success(BooksPage(listOf(randomBook()), null, 1, hiddenCount = null)))
            }

            runTest {
                val vm = FacetBooksViewModel(FacetBooksRoute(FacetKind.Genre, name), repo)
                try {
                    runCurrent()

                    repo.pageRequests shouldBe listOf(
                        TestLibraryRepository.PageRequest(
                            BooksPageQuery(
                                LibrarySort.Title,
                                SortDirection.Asc,
                                facet = BooksFacet(FacetKind.Genre, name),
                            ),
                            cursor = null,
                        ),
                    )
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }

        it("#353 TC-9: shows the empty facet when the genre has no books") {
            val repo = TestLibraryRepository().apply {
                enqueue(LibraryPageResult.Success(BooksPage(emptyList(), null, null, hiddenCount = null)))
            }

            runTest {
                val vm = FacetBooksViewModel(FacetBooksRoute(FacetKind.Genre, randomString()), repo)
                try {
                    runCurrent()

                    vm.uiState.value.body shouldBe FacetBody.Empty
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }
})
