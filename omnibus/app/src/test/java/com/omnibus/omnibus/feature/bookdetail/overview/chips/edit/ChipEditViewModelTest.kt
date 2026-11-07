package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.metadata.MetadataError
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.MetadataRepository
import com.omnibus.omnibus.data.metadata.MetadataWriteResult
import com.omnibus.omnibus.data.metadata.TestMetadataRepository
import com.omnibus.omnibus.data.metadata.VocabularyResult
import com.omnibus.omnibus.data.metadata.randomVocabularyEntry
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val Genres = MetadataListKind.Genres
private val Tags = MetadataListKind.Tags

/** [count] chips. The index in each one keeps them apart from each other and from [newChip], even ignoring case. */
private fun chips(count: Int = 2): List<String> = List(count) { "chip$it-${randomString()}" }

private fun newChip(): String = "new-${randomString()}"

private fun bookWith(genres: List<String> = chips(), tags: List<String> = chips()): Book =
    randomBook().copy(genres = genres, subjects = tags)

private fun saved(genres: List<String> = chips(), tags: List<String> = chips()) =
    MetadataWriteResult.Saved(bookWith(genres, tags))

private val failed = MetadataWriteResult.Failure(MetadataError.Unreachable)

@OptIn(ExperimentalCoroutinesApi::class)
class ChipEditViewModelTest : DescribeSpec({
    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        repository: MetadataRepository = TestMetadataRepository(),
        auth: AuthRepository = TestAuthRepository(),
        connectivity: ConnectivityMonitor = TestConnectivityMonitor(),
        bookUuid: String = randomString(),
    ) = ChipEditViewModel(bookUuid, repository, auth, connectivity)

    fun signedIn(canEdit: Boolean) = TestAuthRepository().apply {
        currentUserState.value = randomCurrentUser().copy(canEdit = canEdit, isAdmin = false)
    }

    /** An online editor's view model with [book] bound. */
    fun editing(
        book: Book = bookWith(),
        repository: MetadataRepository = TestMetadataRepository(),
        bookUuid: String = randomString(),
    ) = sut(repository, signedIn(canEdit = true), TestConnectivityMonitor(online = true), bookUuid).apply { bind(book) }

    describe("binding a book") {
        context("when a kind has a save in flight") {
            it("TC-47: keeps that kind's list while reseeding the other") {
                val book = bookWith()
                val repository = TestMetadataRepository().apply { holdWrite() }
                val vm = editing(book, repository)
                val chip = newChip()
                val fresh = bookWith()
                vm.add(Genres, chip)

                vm.bind(fresh)

                vm.uiState.value.genres shouldBe book.genres + chip
                vm.uiState.value.tags shouldBe fresh.subjects
            }
        }

        context("when a kind's save has settled") {
            it("TC-48: reseeds that kind") {
                val vm = editing(repository = TestMetadataRepository().apply { writeResult = { saved() } })
                val fresh = bookWith()
                vm.add(Genres, newChip())

                vm.bind(fresh)

                vm.uiState.value.genres shouldBe fresh.genres
            }
        }
    }

    describe("adding a chip") {
        it("TC-37: saves the full list") {
            val book = bookWith()
            val bookUuid = randomString()
            val repository = TestMetadataRepository().apply { holdWrite() }
            val chip = newChip()

            editing(book, repository, bookUuid).add(Genres, chip)

            repository.writes shouldBe listOf(TestMetadataRepository.Write(bookUuid, Genres, book.genres + chip))
        }

        context("when the value is refused") {
            it("TC-53: saves nothing") {
                val book = bookWith()
                val repository = TestMetadataRepository()

                editing(book, repository).add(Genres, book.genres.first())

                repository.writes.shouldBeEmpty()
            }
        }
    }

    describe("removing a chip") {
        context("when it is the last one") {
            it("TC-38: saves an empty list") {
                val book = bookWith(genres = chips(1))
                val repository = TestMetadataRepository().apply { holdWrite() }

                editing(book, repository).remove(Genres, book.genres.single())

                repository.writes.single().values.shouldBeEmpty()
            }
        }
    }

    describe("the answer to a save") {
        context("when the newest save is saved") {
            it("TC-39: shows the server's list") {
                val server = chips(3)
                val repository = TestMetadataRepository().apply { writeResult = { saved(genres = server) } }
                val vm = editing(repository = repository)

                vm.add(Genres, newChip())

                vm.uiState.value.genres shouldBe server
            }
        }

        context("when the newest save fails") {
            it("TC-42: restores the list, for no saved server or a failed write") {
                checkAll(Exhaustive.of(MetadataWriteResult.NoServer, failed)) { result ->
                    val book = bookWith()
                    val repository = TestMetadataRepository().apply { writeResult = { result } }
                    val vm = editing(book, repository)

                    vm.add(Genres, newChip())

                    vm.uiState.value.genres shouldBe book.genres
                }
            }

            it("TC-40: reports the failed kind once") {
                val repository = TestMetadataRepository().apply { writeResult = { failed } }
                val vm = editing(repository = repository)

                vm.add(Genres, newChip())
                val failure = vm.uiState.value.failure.shouldNotBeNull()
                failure.kind shouldBe Genres

                vm.failureShown(failure.eventId)
                vm.uiState.value.failure.shouldBeNull()
            }
        }

        context("when an earlier save was confirmed") {
            it("TC-41: a failure restores the confirmed list rather than the opening one") {
                val server = chips(3)
                val repository = TestMetadataRepository().apply { writeResult = { saved(genres = server) } }
                val vm = editing(repository = repository)
                vm.add(Genres, newChip())

                repository.writeResult = { failed }
                vm.add(Genres, newChip())

                vm.uiState.value.genres shouldBe server
            }
        }

        context("when a newer save has started") {
            it("TC-43: ignores an older failure") {
                val book = bookWith()
                val repository = TestMetadataRepository()
                val older = repository.holdWrite()
                repository.holdWrite()
                val vm = editing(book, repository)
                val chips = listOf(newChip(), newChip())
                chips.forEach { vm.add(Genres, it) }

                older.complete(failed)

                vm.uiState.value.genres shouldBe book.genres + chips
                vm.uiState.value.failure.shouldBeNull()
            }

            it("TC-44: an older Saved only updates the list a later failure restores") {
                val book = bookWith()
                val repository = TestMetadataRepository()
                val older = repository.holdWrite()
                val newer = repository.holdWrite()
                val vm = editing(book, repository)
                val chips = listOf(newChip(), newChip())
                chips.forEach { vm.add(Genres, it) }
                val confirmed = chips(3)

                older.complete(saved(genres = confirmed))
                vm.uiState.value.genres shouldBe book.genres + chips

                newer.complete(failed)
                vm.uiState.value.genres shouldBe confirmed
            }
        }

        context("when the save was superseded") {
            it("TC-45: changes nothing") {
                val book = bookWith()
                val repository = TestMetadataRepository().apply { writeResult = { MetadataWriteResult.Superseded } }
                val vm = editing(book, repository)
                val chip = newChip()

                vm.add(Genres, chip)

                vm.uiState.value.genres shouldBe book.genres + chip
                vm.uiState.value.failure.shouldBeNull()
            }
        }

        context("when the other kind has a save in flight") {
            it("TC-46: a failure leaves the other kind's list alone") {
                val book = bookWith()
                val repository = TestMetadataRepository()
                val genres = repository.holdWrite()
                repository.holdWrite()
                val vm = editing(book, repository)
                val tag = newChip()
                vm.add(Genres, newChip())
                vm.add(Tags, tag)

                genres.complete(failed)

                vm.uiState.value.tags shouldBe book.subjects + tag
            }

            it("TC-49: the book offered to the page keeps the other kind's displayed list") {
                val book = bookWith()
                val repository = TestMetadataRepository().apply { holdWrite() }
                val vm = editing(book, repository)
                val tag = newChip()
                vm.add(Tags, tag)

                repository.writeResult = { saved() }
                vm.add(Genres, newChip())

                vm.uiState.value.bookUpdate.shouldNotBeNull().book.subjects shouldBe book.subjects + tag
            }
        }
    }

    describe("refusing edits") {
        /** Tries every edit intent on a bound [vm], and checks that none of them changed anything. */
        fun shouldIgnoreEdits(vm: ChipEditViewModel, repository: TestMetadataRepository) {
            val book = bookWith()
            vm.bind(book)
            val before = vm.uiState.value

            vm.open(Genres)
            vm.add(Genres, newChip())
            vm.remove(Genres, book.genres.first())

            vm.uiState.value shouldBe before
            repository.writes.shouldBeEmpty()
        }

        context("when the device is offline") {
            it("TC-50: ignores every edit") {
                val repository = TestMetadataRepository()
                val vm = sut(repository, signedIn(canEdit = true), TestConnectivityMonitor(online = false))

                shouldIgnoreEdits(vm, repository)
            }
        }

        context("when the user may not edit") {
            it("TC-51: ignores every edit") {
                val repository = TestMetadataRepository()
                val vm = sut(repository, signedIn(canEdit = false), TestConnectivityMonitor(online = true))

                shouldIgnoreEdits(vm, repository)
            }
        }
    }

    describe("opening the sheet") {
        it("TC-55: shows loading, then the kind's values") {
            val entry = randomVocabularyEntry()
            val repository = TestMetadataRepository()
            val held = repository.holdVocabulary(Genres)
            val vm = editing(repository = repository)

            vm.open(Genres)
            vm.uiState.value.pools[Genres] shouldBe PoolState.Loading

            held.complete(VocabularyResult.Success(listOf(entry)))
            vm.uiState.value.pools[Genres] shouldBe PoolState.Loaded(listOf(entry))
        }

        context("when the kind's values have loaded") {
            it("TC-55: does not load them again") {
                val repository = TestMetadataRepository()
                val vm = editing(repository = repository)

                vm.open(Genres)
                vm.close()
                vm.open(Genres)

                repository.vocabularyRequests shouldBe listOf(Genres)
            }
        }

        context("when the values cannot load") {
            it("TC-56: marks the pool as failed, for a failed read or no saved server") {
                checkAll(
                    Exhaustive.of(VocabularyResult.Failure(MetadataError.Unreachable), VocabularyResult.NoServer),
                ) { result ->
                    val repository = TestMetadataRepository().apply { vocabulary[Genres] = result }
                    val vm = editing(repository = repository)

                    vm.open(Genres)

                    vm.uiState.value.pools[Genres] shouldBe PoolState.Failed
                }
            }
        }
    }
})
