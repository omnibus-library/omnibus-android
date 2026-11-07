package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.metadata.TestMetadataRepository
import com.omnibus.omnibus.data.metadata.MetadataError
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.MetadataWriteResult
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.data.metadata.VocabularyResult
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val Genres = MetadataListKind.Genres
private val Tags = MetadataListKind.Tags

/** The editor under test, with the fakes it talks to. */
private class Harness(
    val repository: TestMetadataRepository = TestMetadataRepository(),
    val auth: TestAuthRepository = TestAuthRepository(),
    val connectivity: TestConnectivityMonitor = TestConnectivityMonitor(online = true),
) {
    val viewModel = ChipEditViewModel(
        bookUuid = "uuid-1",
        repository = repository,
        authRepository = auth,
        connectivityMonitor = connectivity,
    )
    val state get() = viewModel.uiState.value
}

private val editor = CurrentUser(id = 1, canEdit = true, canUpload = false, hiddenFormats = emptySet())
private val reader = CurrentUser(id = 2, canEdit = false, canUpload = false, hiddenFormats = emptySet())

/** Book 1 with the given lists. The default has genres Noir and Epic and tag Cozy. */
private fun book(genres: List<String> = listOf("Noir", "Epic"), subjects: List<String> = listOf("Cozy")): Book =
    testBook(1, genres = genres, subjects = subjects)

/** A signed-in editor, with an online device and a book bound. */
private fun Harness.ready(book: Book = book()): Harness = apply {
    auth.currentUserState.value = editor
    viewModel.bind(book)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChipEditViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("bind") {

        it("TC-36: seeds both lists from the book, with the flags from the fakes") {
            val h = Harness().apply { auth.currentUserState.value = editor }
            h.state.bound shouldBe false

            h.viewModel.bind(book(genres = listOf(" Noir ", "", "Epic", "Noir"), subjects = listOf("Cozy")))

            h.state.bound shouldBe true
            h.state.genres shouldBe listOf("Noir", "Epic")
            h.state.tags shouldBe listOf("Cozy")
            h.state.canEdit shouldBe true
            h.state.isOnline shouldBe true
            h.state.openKind.shouldBeNull()
        }

        it("TC-47: skips a kind with a save in flight and reseeds the other") {
            val h = Harness().ready()
            val held = h.repository.holdWrite()
            h.viewModel.add(Genres, "Thriller")

            h.viewModel.bind(book(genres = listOf("Fresh"), subjects = listOf("New")))

            h.state.genres shouldBe listOf("Noir", "Epic", "Thriller")
            h.state.tags shouldBe listOf("New")
            held.complete(MetadataWriteResult.Failure(MetadataError.Unreachable))
            h.state.genres shouldBe listOf("Noir", "Epic")
        }

        it("TC-48: reseeds a kind once its save has settled") {
            val h = Harness().ready()
            h.viewModel.add(Genres, "Thriller")

            h.viewModel.bind(book(genres = listOf("Fresh"), subjects = listOf("Z")))

            h.state.genres shouldBe listOf("Fresh")
            h.state.tags shouldBe listOf("Z")
        }
    }

    describe("add") {

        it("TC-37: shows the new list at once and saves the full list, leaving tags alone") {
            val h = Harness().ready()
            val held = h.repository.holdWrite()

            h.viewModel.add(Genres, "Thriller")

            h.state.genres shouldBe listOf("Noir", "Epic", "Thriller")
            h.state.tags shouldBe listOf("Cozy")
            h.repository.writes shouldBe listOf(
                TestMetadataRepository.Write("uuid-1", Genres, listOf("Noir", "Epic", "Thriller")),
            )
            held.complete(MetadataWriteResult.Superseded)
        }

        it("TC-53: refuses blank, duplicate, over-limit and too-long values without a save") {
            val h = Harness().ready()
            h.viewModel.add(Genres, "   ")
            h.viewModel.add(Genres, "noir")
            h.viewModel.add(Genres, "a".repeat(129))
            h.repository.writes.size shouldBe 0
            h.state.genres shouldBe listOf("Noir", "Epic")

            val full = Harness().ready(book(genres = List(64) { "g$it" }))
            full.viewModel.add(Genres, "one more")
            full.repository.writes.size shouldBe 0
            full.state.genres.size shouldBe 64
        }

        it("TC-54: clears the entry after a pick, accepted or refused, and caps the text at 128") {
            val h = Harness().ready()

            h.viewModel.updateEntry("Thriller")
            h.viewModel.add(Genres, "Thriller")
            h.state.entry shouldBe ""

            h.viewModel.updateEntry("Noir")
            h.viewModel.add(Genres, "Noir")
            h.state.entry shouldBe ""

            h.viewModel.updateEntry("x".repeat(200))
            h.state.entry.length shouldBe 128
        }
    }

    describe("remove") {

        it("TC-38: shows the shorter list at once, and the last chip sends an empty list") {
            val h = Harness().ready(book(genres = listOf("Noir", "Epic"), subjects = listOf("Cozy")))

            h.viewModel.remove(Genres, "Noir")
            h.state.genres shouldBe listOf("Epic")
            h.repository.writes.last() shouldBe TestMetadataRepository.Write("uuid-1", Genres, listOf("Epic"))

            h.viewModel.remove(Tags, "Cozy")
            h.state.tags shouldBe emptyList()
            h.repository.writes.last() shouldBe TestMetadataRepository.Write("uuid-1", Tags, emptyList())
        }
    }

    describe("result") {

        it("TC-39: the newest Saved shows the server list and emits one bookUpdate") {
            val h = Harness().ready()
            h.repository.writeResult = {
                MetadataWriteResult.Saved(testBook(1, genres = listOf("Noir", "Epic", "Server Added")))
            }

            h.viewModel.add(Genres, "Thriller")

            h.state.genres shouldBe listOf("Noir", "Epic", "Server Added")
            val update = h.state.bookUpdate.shouldNotBeNull()
            update.eventId shouldBe 1
            update.book.genres shouldBe listOf("Noir", "Epic", "Server Added")

            h.viewModel.bookUpdateApplied(update.eventId)
            h.state.bookUpdate.shouldBeNull()
        }

        it("TC-39: an ack with a stale eventId leaves a newer update in place") {
            val h = Harness().ready()
            h.repository.writeResult = { MetadataWriteResult.Saved(testBook(1, genres = listOf("N"))) }
            h.viewModel.add(Genres, "A")
            h.viewModel.add(Genres, "B")
            h.state.bookUpdate.shouldNotBeNull().eventId shouldBe 2

            h.viewModel.bookUpdateApplied(1)

            h.state.bookUpdate.shouldNotBeNull().eventId shouldBe 2
        }

        it("TC-40: a failed save restores the opening list and reports once") {
            val h = Harness().ready()
            h.repository.writeResult = { MetadataWriteResult.Failure(MetadataError.Unreachable) }

            h.viewModel.add(Genres, "Thriller")

            h.state.genres shouldBe listOf("Noir", "Epic")
            h.state.failure shouldBe ChipEditFailure(1, Genres)
            h.viewModel.failureShown(1)
            h.state.failure.shouldBeNull()
            h.state.genres shouldBe listOf("Noir", "Epic")
        }

        it("TC-41: a failed save restores the last confirmed list, not the opening one") {
            val h = Harness().ready()
            var calls = 0
            h.repository.writeResult = {
                if (calls++ == 0) MetadataWriteResult.Saved(testBook(1, genres = listOf("S1")))
                else MetadataWriteResult.Failure(MetadataError.Unreachable)
            }

            h.viewModel.add(Genres, "Thriller")
            h.state.genres shouldBe listOf("S1")

            h.viewModel.add(Genres, "Other")
            h.state.genres shouldBe listOf("S1")
            h.state.failure shouldBe ChipEditFailure(2, Genres)
        }

        it("TC-42: every failure kind rolls back and sets exactly one failure") {
            val failures = listOf(
                MetadataWriteResult.NoServer,
                MetadataWriteResult.Failure(MetadataError.Unreachable),
                MetadataWriteResult.Failure(MetadataError.SessionExpired),
                MetadataWriteResult.Failure(MetadataError.Forbidden),
                MetadataWriteResult.Failure(MetadataError.NotFound),
                MetadataWriteResult.Failure(MetadataError.Http(400, "reason")),
                MetadataWriteResult.Failure(MetadataError.UnexpectedResponse),
            )
            failures.forEach { result ->
                val h = Harness().ready()
                h.repository.writeResult = { result }

                h.viewModel.add(Genres, "Thriller")

                h.state.genres shouldBe listOf("Noir", "Epic")
                h.state.failure shouldBe ChipEditFailure(1, Genres)
            }
        }

        it("TC-43: an older failure after a newer save started is ignored") {
            val h = Harness().ready()
            val first = h.repository.holdWrite()
            val second = h.repository.holdWrite()
            h.viewModel.add(Genres, "A")
            h.viewModel.add(Genres, "B")

            first.complete(MetadataWriteResult.Failure(MetadataError.Unreachable))

            h.state.genres shouldBe listOf("Noir", "Epic", "A", "B")
            h.state.failure.shouldBeNull()
            second.complete(MetadataWriteResult.Saved(testBook(1, genres = listOf("Noir", "Epic", "A", "B"))))
        }

        it("TC-44: an older Saved only updates the confirmed list, which a later failure restores") {
            val h = Harness().ready()
            val first = h.repository.holdWrite()
            val second = h.repository.holdWrite()
            h.viewModel.add(Genres, "A")
            h.viewModel.add(Genres, "B")

            first.complete(MetadataWriteResult.Saved(testBook(1, genres = listOf("Noir", "Epic", "A"))))

            h.state.genres shouldBe listOf("Noir", "Epic", "A", "B")
            h.state.bookUpdate.shouldBeNull()

            second.complete(MetadataWriteResult.Failure(MetadataError.Unreachable))
            h.state.genres shouldBe listOf("Noir", "Epic", "A")
            h.state.failure shouldBe ChipEditFailure(1, Genres)
        }

        it("TC-45: Superseded changes nothing") {
            val h = Harness().ready()
            h.repository.writeResult = { MetadataWriteResult.Superseded }

            h.viewModel.add(Genres, "Thriller")

            h.state.genres shouldBe listOf("Noir", "Epic", "Thriller")
            h.state.failure.shouldBeNull()
            h.state.bookUpdate.shouldBeNull()
        }

        it("TC-46: a genres failure leaves tags, and a tags save in flight, alone") {
            val h = Harness().ready()
            val genresHold = h.repository.holdWrite()
            val tagsHold = h.repository.holdWrite()
            h.viewModel.add(Genres, "A")
            h.viewModel.add(Tags, "t")

            genresHold.complete(MetadataWriteResult.Failure(MetadataError.Unreachable))

            h.state.genres shouldBe listOf("Noir", "Epic")
            h.state.tags shouldBe listOf("Cozy", "t")
            h.state.failure shouldBe ChipEditFailure(1, Genres)

            tagsHold.complete(MetadataWriteResult.Saved(testBook(1, subjects = listOf("Cozy", "t"))))
            h.state.tags shouldBe listOf("Cozy", "t")
            h.state.failure shouldBe ChipEditFailure(1, Genres)
        }
    }

    describe("bookUpdate") {

        it("TC-49: keeps the optimistic list of a kind still in flight, both ways") {
            val tagsInFlight = Harness().ready()
            val tagsHold = tagsInFlight.repository.holdWrite()
            tagsInFlight.viewModel.add(Tags, "t")
            tagsInFlight.repository.writeResult = {
                MetadataWriteResult.Saved(testBook(1, genres = listOf("Noir", "Epic", "A")))
            }
            tagsInFlight.viewModel.add(Genres, "A")
            tagsInFlight.state.bookUpdate.shouldNotBeNull().book.subjects shouldBe listOf("Cozy", "t")

            val genresInFlight = Harness().ready()
            val genresHold = genresInFlight.repository.holdWrite()
            genresInFlight.viewModel.add(Genres, "A")
            genresInFlight.repository.writeResult = {
                MetadataWriteResult.Saved(testBook(1, subjects = listOf("Cozy", "t")))
            }
            genresInFlight.viewModel.add(Tags, "t")
            genresInFlight.state.bookUpdate.shouldNotBeNull().book.genres shouldBe listOf("Noir", "Epic", "A")

            tagsHold.complete(MetadataWriteResult.Superseded)
            genresHold.complete(MetadataWriteResult.Superseded)
        }
    }

    describe("offline") {

        it("TC-50: ignores open, add and remove, and makes no repository call") {
            val h = Harness(connectivity = TestConnectivityMonitor(online = false)).ready()

            h.viewModel.open(Genres)
            h.viewModel.add(Genres, "Thriller")
            h.viewModel.remove(Genres, "Noir")

            h.state.openKind.shouldBeNull()
            h.state.genres shouldBe listOf("Noir", "Epic")
            h.repository.writes.size shouldBe 0
            h.repository.vocabularyRequests.size shouldBe 0
        }
    }

    describe("permission") {

        it("TC-51: ignores every edit intent for a user who may not edit") {
            listOf<CurrentUser?>(null, reader).forEach { user ->
                val h = Harness().apply {
                    auth.currentUserState.value = user
                    viewModel.bind(book())
                }

                h.viewModel.open(Genres)
                h.viewModel.add(Genres, "Thriller")
                h.viewModel.remove(Genres, "Noir")

                h.state.openKind.shouldBeNull()
                h.state.genres shouldBe listOf("Noir", "Epic")
                h.repository.writes.size shouldBe 0
                h.repository.vocabularyRequests.size shouldBe 0
            }
        }

        it("TC-52: follows connectivity and the signed-in user") {
            val h = Harness().apply { viewModel.bind(book()) }

            h.connectivity.isOnline.value = false
            h.auth.currentUserState.value = editor
            h.viewModel.add(Genres, "A")
            h.repository.writes.size shouldBe 0

            h.connectivity.isOnline.value = true
            h.state.canEdit shouldBe true
            h.viewModel.add(Genres, "A")
            h.repository.writes.size shouldBe 1

            h.auth.currentUserState.value = reader
            h.state.canEdit shouldBe false
        }
    }

    describe("open") {

        it("TC-55: loads each kind's values once, deduplicated and sorted, while the load is Loading") {
            val h = Harness().ready()
            h.repository.vocabulary[Genres] = VocabularyResult.Success(
                listOf(VocabularyEntry("fantasy", 3), VocabularyEntry("Fantasy", 9), VocabularyEntry("Cozy", 1)),
            )
            val held = h.repository.holdVocabulary(Genres)

            h.viewModel.open(Genres)
            h.state.pools[Genres] shouldBe PoolState.Loading
            held.complete(h.repository.vocabulary.getValue(Genres))

            h.state.pools[Genres] shouldBe PoolState.Loaded(
                listOf(VocabularyEntry("Cozy", 1), VocabularyEntry("fantasy", 9)),
            )
            h.viewModel.close()
            h.viewModel.open(Genres)
            h.repository.vocabularyRequests shouldBe listOf(Genres)

            h.viewModel.open(Tags)
            h.repository.vocabularyRequests shouldBe listOf(Genres, Tags)
        }

        it("TC-56: a failed pool leaves adding as new working") {
            listOf<VocabularyResult>(
                VocabularyResult.Failure(MetadataError.Unreachable),
                VocabularyResult.NoServer,
            ).forEach { failed ->
                val h = Harness().ready()
                h.repository.vocabulary[Genres] = failed

                h.viewModel.open(Genres)
                h.state.pools[Genres] shouldBe PoolState.Failed

                h.viewModel.add(Genres, "Grimdark")
                h.state.genres shouldBe listOf("Noir", "Epic", "Grimdark")
            }
        }
    }

    describe("close") {

        it("TC-57: clears the sheet and lets saves finish") {
            val h = Harness().ready()
            val held = h.repository.holdWrite()
            h.viewModel.open(Genres)
            h.viewModel.add(Genres, "A")
            h.viewModel.updateEntry("zz")

            h.viewModel.close()

            h.state.openKind.shouldBeNull()
            h.state.entry shouldBe ""
            held.complete(MetadataWriteResult.Saved(testBook(1, genres = listOf("Noir", "Epic", "A"))))
            h.state.genres shouldBe listOf("Noir", "Epic", "A")
            h.state.bookUpdate.shouldNotBeNull().eventId shouldBe 1
        }
    }
})
