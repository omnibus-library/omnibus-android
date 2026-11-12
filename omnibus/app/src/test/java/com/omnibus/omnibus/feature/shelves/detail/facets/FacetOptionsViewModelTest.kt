package com.omnibus.omnibus.feature.shelves.detail.facets

import com.omnibus.omnibus.data.authors.AuthorListResult
import com.omnibus.omnibus.data.authors.AuthorsError
import com.omnibus.omnibus.data.authors.TestAuthorsRepository
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomShelfPreview
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.TestMetadataRepository
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.data.metadata.VocabularyResult
import com.omnibus.omnibus.data.series.SeriesError
import com.omnibus.omnibus.data.series.SeriesListResult
import com.omnibus.omnibus.data.series.TestSeriesRepository
import com.omnibus.omnibus.data.shelves.FilterField
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
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

    fun sut(
        field: FilterField,
        metadata: TestMetadataRepository = TestMetadataRepository(),
        authors: TestAuthorsRepository = TestAuthorsRepository(),
        series: TestSeriesRepository = TestSeriesRepository(),
        library: TestLibraryRepository = TestLibraryRepository(),
    ) = FacetOptionsViewModel(field, metadata, authors, series, library)

    describe("reading the options") {

        it("#232 TC-26: reads tags and genres from the library's vocabulary") {
            checkAll(Exhaustive.of(FilterField.Tag to MetadataListKind.Tags, FilterField.Genre to MetadataListKind.Genres)) { (field, kind) ->
                val entries = List(2) { VocabularyEntry(randomString(), randomLong(1L..50L)) }
                val metadata = TestMetadataRepository().apply { vocabulary[kind] = VocabularyResult.Success(entries) }

                sut(field, metadata = metadata).uiState.value shouldBe FacetOptionsState.Loaded(
                    entries.map { FacetOption(it.name, it.name, it.count) },
                )
            }
        }

        it("#232 TC-26: reads authors and series from their lists") {
            val authors = TestAuthorsRepository().apply {
                listResult = AuthorListResult.Success(listOf(VocabularyEntry("Le Guin", 3)))
            }
            val series = TestSeriesRepository().apply {
                listResult = SeriesListResult.Success(listOf(VocabularyEntry("Earthsea", 6)))
            }

            sut(FilterField.Author, authors = authors).uiState.value shouldBe
                FacetOptionsState.Loaded(listOf(FacetOption("Le Guin", "Le Guin", 3)))
            sut(FilterField.Series, series = series).uiState.value shouldBe
                FacetOptionsState.Loaded(listOf(FacetOption("Earthsea", "Earthsea", 6)))
        }

        it("#232 TC-26: offers each shelf by its id, with its name and book count") {
            val preview = randomShelfPreview()
            val library = TestLibraryRepository().apply {
                defaultShelfPreviews = ShelfPreviewsResult.Success(listOf(preview))
            }

            sut(FilterField.Shelf, library = library).uiState.value shouldBe FacetOptionsState.Loaded(
                listOf(FacetOption(preview.shelf.id.toString(), preview.shelf.name, preview.shelf.bookCount)),
            )
        }

        it("#232 TC-26: offers the formats of both categories, with no counts") {
            sut(FilterField.Format).uiState.value shouldBe FacetOptionsState.Loaded(
                (LibraryCategory.Ebooks.formats + LibraryCategory.Audiobooks.formats).map {
                    FacetOption(it, it.uppercase(), null)
                },
            )
        }

        it("#232 TC-26: shows Failed when the read fails") {
            checkAll(Exhaustive.of(FilterField.Tag, FilterField.Author, FilterField.Series)) { field ->
                val metadata = TestMetadataRepository().apply { vocabulary[MetadataListKind.Tags] = VocabularyResult.NoServer }

                sut(field, metadata = metadata).uiState.value shouldBe FacetOptionsState.Failed
            }
        }

        it("#232 TC-26: reads the options again on retry") {
            val metadata = TestMetadataRepository().apply { vocabulary[MetadataListKind.Genres] = VocabularyResult.NoServer }
            val viewModel = sut(FilterField.Genre, metadata = metadata)
            viewModel.uiState.value shouldBe FacetOptionsState.Failed

            val entry = VocabularyEntry("Fantasy", 9)
            metadata.vocabulary[MetadataListKind.Genres] = VocabularyResult.Success(listOf(entry))
            viewModel.retry()

            viewModel.uiState.value shouldBe FacetOptionsState.Loaded(listOf(FacetOption("Fantasy", "Fantasy", 9)))
        }

        it("#232 TC-26: reports an author read that fails with an error as Failed") {
            val authors = TestAuthorsRepository().apply {
                listResult = AuthorListResult.Failure(AuthorsError.Unreachable)
            }

            sut(FilterField.Author, authors = authors).uiState.value shouldBe FacetOptionsState.Failed
        }
    }
})
