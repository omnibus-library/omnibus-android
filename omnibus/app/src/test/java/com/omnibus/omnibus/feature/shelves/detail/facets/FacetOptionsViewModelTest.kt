package com.omnibus.omnibus.feature.shelves.detail.facets

import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomShelfPreview
import com.omnibus.omnibus.data.shelves.FilterField
import io.kotest.core.spec.style.DescribeSpec
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
class FacetOptionsViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("reading the options") {

        it("#232 TC-26: offers the values the shelf's own books carry, with no read") {
            checkAll(Exhaustive.of(FilterField.Tag, FilterField.Genre, FilterField.Format, FilterField.Author, FilterField.Series)) { field ->
                val books = List(3) { randomBook() }

                FacetOptionsViewModel(field, books, TestLibraryRepository()).uiState.value shouldBe
                    FacetOptionsState.Loaded(shelfFacetOptions(field, books))
            }
        }

        it("#232 TC-26: offers each shelf by its id, with its name and book count") {
            val preview = randomShelfPreview()
            val library = TestLibraryRepository().apply {
                defaultShelfPreviews = ShelfPreviewsResult.Success(listOf(preview))
            }

            FacetOptionsViewModel(FilterField.Shelf, emptyList(), library).uiState.value shouldBe FacetOptionsState.Loaded(
                listOf(FacetOption(preview.shelf.id.toString(), preview.shelf.name, preview.shelf.bookCount)),
            )
        }

        it("#232 TC-26: shows Failed when the shelves cannot be read, and reads them again on retry") {
            val library = TestLibraryRepository().apply { defaultShelfPreviews = ShelfPreviewsResult.NoServer }
            val viewModel = FacetOptionsViewModel(FilterField.Shelf, emptyList(), library)
            viewModel.uiState.value shouldBe FacetOptionsState.Failed

            val preview = randomShelfPreview()
            library.defaultShelfPreviews = ShelfPreviewsResult.Success(listOf(preview))
            viewModel.retry()

            viewModel.uiState.value shouldBe FacetOptionsState.Loaded(
                listOf(FacetOption(preview.shelf.id.toString(), preview.shelf.name, preview.shelf.bookCount)),
            )
        }
    }
})
