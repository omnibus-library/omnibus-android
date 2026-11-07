package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightsRepository
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A highlight [twoChapters] places in chapter 2. */
private fun placeable(): Highlight = randomHighlight().copy(format = "epub", location = "epubcfi(/6/4)", fraction = 0.6f)

private fun twoChapters() = BookChaptersResult.Success(
    ebookChapters = listOf(BookChapter(0, null, 0f), BookChapter(1, null, 0.5f)),
    audioChapters = emptyList(),
    audioFileId = null,
    audioTotalSeconds = null,
)

/** A chapters source whose first call waits on [gate]. */
private class GatedBookProgress(private val gate: CompletableDeferred<Unit>) : BookProgressRepository {
    val chapterCalls = mutableListOf<String>()

    override fun getBookProgressFlow(bookUuid: String): Flow<BookProgressResult> = emptyFlow()

    override suspend fun getChapters(bookUuid: String): BookChaptersResult {
        chapterCalls += bookUuid
        gate.await()
        return twoChapters()
    }

    override suspend fun getSessions(bookUuid: String): ReadingSessionsResult =
        ReadingSessionsResult.Success(emptyList())
}

/** Runs [block] with [vm]'s state kept active, as a subscribed screen would. */
private inline fun collecting(vm: HighlightsViewModel, block: HighlightsViewModel.() -> Unit) {
    val scope = CollectingScope()
    scope.collect(vm.uiState)
    try {
        vm.block()
    } finally {
        scope.cancel()
    }
}

private fun HighlightsViewModel.rows() = uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows

@OptIn(ExperimentalCoroutinesApi::class)
class HighlightsViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        repository: HighlightsRepository = TestHighlightsRepository(),
        progress: BookProgressRepository = TestBookProgressRepository(),
        timeSource: TimeSource = TestTimeSource(randomLong()),
    ) = HighlightsViewModel(bookUuid, repository, progress, timeSource)

    describe("the list state") {

        context("when the highlights could not be read") {
            it("TC-27: shows the failed state") {
                val uuid = randomString()
                val repository = TestHighlightsRepository()
                repository.seed(uuid, HighlightsState.Failed)

                collecting(sut(uuid, repository)) {
                    uiState.value.content shouldBe HighlightsUIContent.Failed
                }
            }
        }

        context("when the highlights have loaded") {
            it("TC-27: keeps the repository's order") {
                val uuid = randomString()
                val highlights = List(5) { randomHighlight() }
                val repository = TestHighlightsRepository()
                repository.seed(uuid, HighlightsState.Loaded(highlights))

                collecting(sut(uuid, repository)) {
                    rows().map { it.id } shouldBe highlights.map { it.id }
                }
            }
        }

        context("when its fields are blank") {
            it("TC-28: reads as absent") {
                val uuid = randomString()
                val repository = TestHighlightsRepository()
                repository.seed(uuid, HighlightsState.Loaded(listOf(randomHighlight().copy(text = "  ", note = ""))))

                collecting(sut(uuid, repository)) {
                    rows().single().text shouldBe null
                    rows().single().note shouldBe null
                }
            }
        }

        context("when the chapters have loaded") {
            it("TC-29: numbers the rows it can place") {
                val uuid = randomString()
                val repository = TestHighlightsRepository()
                repository.seed(uuid, HighlightsState.Loaded(listOf(placeable())))
                val progress = TestBookProgressRepository()
                progress.chaptersResults.addLast(twoChapters())

                collecting(sut(uuid, repository, progress)) {
                    reload(1)

                    rows().single().chapterNumber shouldBe 2
                }
            }
        }

        context("when the chapters could not be read") {
            it("TC-29: keeps the rows with no numbers") {
                val uuid = randomString()
                val repository = TestHighlightsRepository()
                repository.seed(uuid, HighlightsState.Loaded(listOf(placeable())))
                val progress = TestBookProgressRepository()
                progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))

                collecting(sut(uuid, repository, progress)) {
                    reload(1)

                    rows().single().chapterNumber shouldBe null
                }
            }
        }
    }

    describe("reloading") {

        it("TC-30: refreshes once per key") {
            val uuid = randomString()
            val repository = TestHighlightsRepository()
            val vm = sut(uuid, repository)

            vm.reload(1)
            vm.reload(1)
            vm.reload(2)

            repository.refreshCalls shouldBe listOf(uuid, uuid)
        }

        context("when the key is new") {

            context("when the chapters failed") {
                it("TC-31: tries them again") {
                    val uuid = randomString()
                    val repository = TestHighlightsRepository()
                    repository.seed(uuid, HighlightsState.Loaded(listOf(placeable())))
                    val progress = TestBookProgressRepository()
                    progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))
                    progress.chaptersResults.addLast(twoChapters())

                    collecting(sut(uuid, repository, progress)) {
                        reload(1)
                        reload(2)

                        rows().single().chapterNumber shouldBe 2
                    }
                }
            }

            context("when the chapters are still being read") {
                it("TC-31: does not ask twice") {
                    val gated = GatedBookProgress(CompletableDeferred())
                    val vm = sut(progress = gated)

                    vm.reload(1)
                    vm.reload(2)

                    gated.chapterCalls.size shouldBe 1
                }
            }
        }
    }

    describe("retrying") {

        context("when the highlights failed") {
            context("when the refresh succeeds") {
                it("TC-32: shows the list") {
                    val uuid = randomString()
                    val highlight = randomHighlight()
                    val repository = TestHighlightsRepository()
                    repository.seed(uuid, HighlightsState.Failed)
                    repository.refreshResults.addLast(HighlightsState.Loaded(listOf(highlight)))

                    collecting(sut(uuid, repository)) {
                        retry()

                        rows().map { it.id } shouldBe listOf(highlight.id)
                    }
                }
            }
        }
    }
})
