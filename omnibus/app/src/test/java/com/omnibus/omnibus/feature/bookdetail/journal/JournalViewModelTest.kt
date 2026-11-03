package com.omnibus.omnibus.feature.bookdetail.journal

import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.FakeAuthRepository
import com.omnibus.omnibus.data.journals.FakeJournalsRepository
import com.omnibus.omnibus.data.journals.JournalEntry
import com.omnibus.omnibus.data.journals.JournalsState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val UUID = "A"

/** 2025-10-24 00:00 UTC. */
private const val OCT_24_2025 = 1_761_264_000_000L

private fun entry(
    id: Long,
    authorId: Long = 8,
    authorName: String = "Ben",
    body: String = "Passage $id",
    isDraft: Boolean = false,
    createdAtMillis: Long? = OCT_24_2025,
) = JournalEntry(
    id = id,
    authorId = authorId,
    authorName = authorName,
    bodyMd = body,
    isDraft = isDraft,
    createdAtMillis = createdAtMillis,
)

private fun viewer(id: Long) = CurrentUser(id = id, canEdit = false, canUpload = false, hiddenFormats = emptySet())

/** A ViewModel with its fakes. Its state is kept active by [collecting] until the test ends. */
private class Harness(authRepository: AuthRepository) {
    val repository = FakeJournalsRepository()
    val collecting = CollectingScope()
    val vm = JournalViewModel(UUID, repository, authRepository, TimeSource { 1_000L })
        .also { collecting.collect(it.uiState) }
}

/** Runs [block] on a [Harness] built over [auth], and cancels its collection afterwards. */
private inline fun withHarness(auth: AuthRepository = FakeAuthRepository(), block: Harness.() -> Unit) {
    val harness = Harness(auth)
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

/** The cards of a Loaded state, or a failure of the test. */
private fun Harness.cards(): List<JournalCardModel> =
    (vm.uiState.value.content as JournalUIContent.Loaded).cards

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the list state") {

        it("TC-36: Loaded cards keep the repository's order and carry their fields and runs; nowMillis comes from the clock") {
            val auth = FakeAuthRepository()
            withHarness(auth) {
                auth.currentUserState.value = viewer(7)
                repository.seed(
                    UUID,
                    JournalsState.Loaded(
                        listOf(
                            entry(1, authorId = 7, authorName = "Ann", body = "He was ||Ares||."),
                            entry(2, authorId = 8, authorName = "Ben", body = "Second"),
                            entry(3, authorId = 7, authorName = "Ann", body = "Draft", isDraft = true),
                        ),
                    ),
                )

                vm.uiState.value.nowMillis shouldBe 1_000L
                cards() shouldBe listOf(
                    JournalCardModel(
                        id = 1,
                        authorName = "Ann",
                        isOwn = true,
                        isDraft = false,
                        createdAtMillis = OCT_24_2025,
                        preview = listOf(
                            PreviewRun.Text("He was "),
                            PreviewRun.Veiled("lore"),
                            PreviewRun.Text("."),
                        ),
                    ),
                    JournalCardModel(
                        id = 2,
                        authorName = "Ben",
                        isOwn = false,
                        isDraft = false,
                        createdAtMillis = OCT_24_2025,
                        preview = listOf(PreviewRun.Text("Second")),
                    ),
                    JournalCardModel(
                        id = 3,
                        authorName = "Ann",
                        isOwn = true,
                        isDraft = true,
                        createdAtMillis = OCT_24_2025,
                        preview = listOf(PreviewRun.Text("Draft")),
                    ),
                )
            }
        }

        it("TC-37: waiting is Loading, and it never claims an empty list or a failure") {
            withHarness {
                vm.uiState.value.content shouldBe JournalUIContent.Loading

                repository.seed(UUID, JournalsState.Loading)
                vm.uiState.value.content shouldBe JournalUIContent.Loading
            }
        }

        it("TC-38: an empty journal is Loaded with no cards") {
            withHarness {
                repository.seed(UUID, JournalsState.Loaded(emptyList()))

                vm.uiState.value.content shouldBe JournalUIContent.Loaded(emptyList())
            }
        }

        it("TC-39: a retry from Failed shows Loading while it runs, then Loaded; every retry calls refresh") {
            withHarness {
                repository.seed(UUID, JournalsState.Failed)
                vm.uiState.value.content shouldBe JournalUIContent.Failed

                val gate = CompletableDeferred<Unit>()
                repository.refreshGates.addLast(gate)
                repository.refreshResults.addLast(JournalsState.Loaded(listOf(entry(1))))
                vm.retry()
                vm.uiState.value.content shouldBe JournalUIContent.Loading

                gate.complete(Unit)
                cards().map { it.id } shouldBe listOf(1L)

                // A second retry with nothing scripted still calls refresh, and the loaded list stays.
                vm.retry()
                repository.refreshCalls shouldBe listOf(UUID, UUID)
                cards().map { it.id } shouldBe listOf(1L)
            }
        }

        it("TC-40: a failed refresh keeps the loaded list") {
            withHarness {
                repository.seed(UUID, JournalsState.Loaded(listOf(entry(1))))
                repository.refreshResults.addLast(JournalsState.Failed)

                vm.retry()

                cards().map { it.id } shouldBe listOf(1L)
            }
        }

        it("TC-41: own entries are the viewer's; with no viewer, and when the session read throws, every entry is another reader's") {
            val auth = FakeAuthRepository()
            withHarness(auth) {
                repository.seed(UUID, JournalsState.Loaded(listOf(entry(1, authorId = 7), entry(2, authorId = 8))))
                auth.currentUserState.value = viewer(7)
                cards().map { it.isOwn } shouldBe listOf(true, false)
            }

            withHarness {
                repository.seed(UUID, JournalsState.Loaded(listOf(entry(1, authorId = 7), entry(2, authorId = 8))))
                cards().map { it.isOwn } shouldBe listOf(false, false)
            }

            val throwing = object : AuthRepository by FakeAuthRepository() {
                override val currentUser: Flow<CurrentUser?> = flow { throw IllegalStateException("boom") }
            }
            withHarness(throwing) {
                repository.seed(UUID, JournalsState.Loaded(listOf(entry(1, authorId = 7))))
                cards().map { it.isOwn } shouldBe listOf(false)
            }
        }

        it("TC-42: a viewer id that arrives after Loaded re-derives ownership without a refetch") {
            val auth = FakeAuthRepository()
            withHarness(auth) {
                repository.seed(UUID, JournalsState.Loaded(listOf(entry(1, authorId = 7))))
                cards().map { it.isOwn } shouldBe listOf(false)

                auth.currentUserState.value = viewer(7)

                cards().map { it.isOwn } shouldBe listOf(true)
                repository.refreshCalls shouldBe emptyList()
            }
        }

        it("TC-43: reload refreshes once per distinct key") {
            withHarness {
                vm.reload(1)
                repository.refreshCalls shouldBe listOf(UUID)

                vm.reload(1)
                repository.refreshCalls shouldBe listOf(UUID)

                vm.reload(2)
                repository.refreshCalls shouldBe listOf(UUID, UUID)
            }
        }

        it("TC-44: the card's preview comes from the masked runs, and the real spoiler is in no part of the card") {
            withHarness {
                repository.seed(UUID, JournalsState.Loaded(listOf(entry(1, body = "He was ||Ares||."))))

                val card = cards().single()

                card.preview.any { it is PreviewRun.Veiled && it.filler == "lore" } shouldBe true
                card.toString().contains("Ares") shouldBe false
            }
        }
    }
})
