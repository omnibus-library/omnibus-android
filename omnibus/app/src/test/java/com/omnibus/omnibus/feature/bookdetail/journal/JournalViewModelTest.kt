package com.omnibus.omnibus.feature.bookdetail.journal

import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.journals.JournalsRepository
import com.omnibus.omnibus.data.journals.JournalsState
import com.omnibus.omnibus.data.journals.TestJournalsRepository
import com.omnibus.omnibus.data.journals.randomJournalEntry
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun viewer(id: Long) =
    CurrentUser(id = id, canEdit = randomBoolean(), canUpload = randomBoolean(), hiddenFormats = emptySet())

/** Runs [block] with [vm]'s state kept active, as a subscribed screen would. */
private inline fun collecting(vm: JournalViewModel, block: JournalViewModel.() -> Unit) {
    val scope = CollectingScope()
    scope.collect(vm.uiState)
    try {
        vm.block()
    } finally {
        scope.cancel()
    }
}

private fun JournalViewModel.cards() = uiState.value.content.shouldBeInstanceOf<JournalUIContent.Loaded>().cards

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        repository: JournalsRepository = TestJournalsRepository(),
        auth: AuthRepository = TestAuthRepository(),
        timeSource: TimeSource = TestTimeSource(randomLong()),
    ) = JournalViewModel(bookUuid, repository, auth, timeSource)

    describe("the list state") {

        context("when nothing has loaded") {
            it("TC-37: shows the loading state") {
                collecting(sut()) {
                    uiState.value.content shouldBe JournalUIContent.Loading
                }
            }
        }

        context("when the entries have loaded") {
            it("TC-36: keeps the repository's order on cards carrying each entry's fields") {
                val uuid = randomString()
                val entries = List(3) { randomJournalEntry().copy(bodyMd = "Passage") }
                val repository = TestJournalsRepository()
                repository.seed(uuid, JournalsState.Loaded(entries))

                collecting(sut(uuid, repository)) {
                    cards() shouldBe entries.map {
                        JournalCardModel(
                            id = it.id,
                            authorName = it.authorName,
                            isOwn = false,
                            isDraft = it.isDraft,
                            createdAtMillis = it.createdAtMillis,
                            preview = listOf(PreviewRun.Text("Passage")),
                        )
                    }
                }
            }
        }

        context("when an entry has a spoiler") {
            it("TC-44: keeps the real text out of the whole card") {
                val uuid = randomString()
                val repository = TestJournalsRepository()
                repository.seed(uuid, JournalsState.Loaded(listOf(randomJournalEntry().copy(bodyMd = "He was ||Ares||."))))

                collecting(sut(uuid, repository)) {
                    cards().single().toString() shouldNotContain "Ares"
                }
            }
        }
    }

    describe("which entries are the viewer's own") {

        context("when the viewer is known") {
            it("TC-41: marks only the viewer's entries") {
                val uuid = randomString()
                val id = randomLong()
                val repository = TestJournalsRepository()
                repository.seed(
                    uuid,
                    JournalsState.Loaded(
                        listOf(randomJournalEntry().copy(authorId = id), randomJournalEntry().copy(authorId = id + 1)),
                    ),
                )
                val auth = TestAuthRepository()
                auth.currentUserState.value = viewer(id)

                collecting(sut(uuid, repository, auth)) {
                    cards().map { it.isOwn } shouldBe listOf(true, false)
                }
            }
        }

        context("when the session read throws") {
            it("TC-41: marks none") {
                val uuid = randomString()
                val repository = TestJournalsRepository()
                repository.seed(uuid, JournalsState.Loaded(listOf(randomJournalEntry())))
                val throwing = object : AuthRepository by TestAuthRepository() {
                    override val currentUser: Flow<CurrentUser?> = flow { throw IllegalStateException("boom") }
                }

                collecting(sut(uuid, repository, throwing)) {
                    cards().single().isOwn shouldBe false
                }
            }
        }

        context("when the viewer arrives after the entries") {
            it("TC-42: re-derives ownership without a refetch") {
                val uuid = randomString()
                val entry = randomJournalEntry()
                val repository = TestJournalsRepository()
                repository.seed(uuid, JournalsState.Loaded(listOf(entry)))
                val auth = TestAuthRepository()

                collecting(sut(uuid, repository, auth)) {
                    auth.currentUserState.value = viewer(entry.authorId)

                    cards().single().isOwn shouldBe true
                    repository.refreshCalls shouldBe emptyList()
                }
            }
        }
    }

    describe("reloading") {

        it("TC-43: refreshes once per key") {
            val uuid = randomString()
            val repository = TestJournalsRepository()
            val vm = sut(uuid, repository)

            vm.reload(1)
            vm.reload(1)
            vm.reload(2)

            repository.refreshCalls shouldBe listOf(uuid, uuid)
        }
    }

    describe("retrying") {

        context("when the entries failed") {
            it("TC-39: shows the list once the refresh succeeds") {
                val uuid = randomString()
                val entry = randomJournalEntry()
                val repository = TestJournalsRepository()
                repository.seed(uuid, JournalsState.Failed)
                repository.refreshResults.addLast(JournalsState.Loaded(listOf(entry)))

                collecting(sut(uuid, repository)) {
                    retry()

                    cards().map { it.id } shouldBe listOf(entry.id)
                }
            }
        }
    }
})
