package com.omnibus.omnibus.feature.bookdetail.highlights

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.FakeBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.highlights.FakeHighlightsRepository
import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.CollectingScope
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

private const val UUID = "A"

private fun highlight(
    id: Long,
    text: String? = "t$id",
    note: String? = null,
    color: HighlightColor = HighlightColor.Amber,
    format: String? = "epub",
    location: String? = "epubcfi(/6/4)",
    fraction: Float? = 0.6f,
) = Highlight(
    id = id,
    text = text,
    note = note,
    color = color,
    location = location,
    format = format,
    createdAtMillis = 1_761_775_445_000L,
    fraction = fraction,
)

private fun twoChapters() = BookChaptersResult.Success(
    ebookChapters = listOf(BookChapter(0, null, 0f), BookChapter(1, null, 0.5f)),
    audioChapters = emptyList(),
    audioFileId = null,
    audioTotalSeconds = null,
)

/** A ViewModel with its fakes, and its state kept active by [collecting] until the test ends. */
private class Harness {
    val repository = FakeHighlightsRepository()
    val progress = FakeBookProgressRepository()
    val collecting = CollectingScope()
    val vm = HighlightsViewModel(UUID, repository, progress, TimeSource { 1_000L })
        .also { collecting.collect(it.uiState) }
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(block: Harness.() -> Unit) {
    val harness = Harness()
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

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

@OptIn(ExperimentalCoroutinesApi::class)
class HighlightsViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the list state") {

        it("TC-27: Loading, Failed and Loaded map to their content, and the rows keep the repository's order") {
            withHarness {
                repository.seed(UUID, HighlightsState.Loading)
                vm.uiState.value.content shouldBe HighlightsUIContent.Loading

                repository.seed(UUID, HighlightsState.Failed)
                vm.uiState.value.content shouldBe HighlightsUIContent.Failed

                val same = highlight(5, text = "same", color = HighlightColor.Blue)
                val twin = highlight(7, text = "same", color = HighlightColor.Blue)
                repository.seed(
                    UUID,
                    HighlightsState.Loaded(listOf(highlight(9), highlight(2), same, twin, highlight(8))),
                )
                val content = vm.uiState.value.content
                content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.map { it.id } shouldBe listOf(9L, 2L, 5L, 7L, 8L)
                content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.map { it.id }.distinct().size shouldBe 5
                vm.uiState.value.nowMillis shouldBe 1_000L
            }
        }

        it("TC-28: blank text and note read as absent; other text is kept as given") {
            withHarness {
                repository.seed(
                    UUID,
                    HighlightsState.Loaded(
                        listOf(
                            highlight(1, text = null, note = null),
                            highlight(2, text = "", note = ""),
                            highlight(3, text = "  ", note = "  "),
                            highlight(4, text = " kept ", note = " kept "),
                        ),
                    ),
                )
                val rows = vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows
                rows.map { it.text } shouldBe listOf(null, null, null, " kept ")
                rows.map { it.note } shouldBe listOf(null, null, null, " kept ")
            }
        }

        it("TC-29: chapter numbers come from the chapters; a failed chapters read leaves every number out, with no error") {
            withHarness {
                progress.chaptersResults.addLast(twoChapters())
                repository.seed(
                    UUID,
                    HighlightsState.Loaded(
                        listOf(
                            highlight(1, fraction = 0.6f),
                            highlight(2, format = "pdf", location = "pdf:12", fraction = 0.6f),
                            highlight(3, format = null, location = null, fraction = null),
                        ),
                    ),
                )
                vm.reload(1)
                vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.map { it.chapterNumber } shouldBe listOf(2, null, null)
            }

            withHarness {
                progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))
                repository.seed(
                    UUID,
                    HighlightsState.Loaded(
                        listOf(
                            highlight(1, fraction = 0.6f),
                            highlight(2, format = "pdf", location = "pdf:12", fraction = 0.6f),
                            highlight(3, format = null, location = null, fraction = null),
                        ),
                    ),
                )
                vm.reload(1)
                val content = vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>()
                content.rows.map { it.id } shouldBe listOf(1L, 2L, 3L)
                content.rows.map { it.chapterNumber } shouldBe listOf(null, null, null)
            }
        }
    }

    describe("loading") {

        it("TC-30: a key refreshes once, the chapters load once after a success, and a repeated key does nothing") {
            withHarness {
                vm.reload(1)
                repository.refreshCalls shouldBe listOf(UUID)
                progress.chaptersCalls shouldBe listOf(UUID)

                vm.reload(1)
                repository.refreshCalls shouldBe listOf(UUID)

                vm.reload(2)
                repository.refreshCalls shouldBe listOf(UUID, UUID)
                progress.chaptersCalls.size shouldBe 1
            }
        }

        it("TC-31: a failed chapters read is tried again on the next key; a pending read is not asked for twice") {
            withHarness {
                progress.chaptersResults.addLast(BookChaptersResult.Failed(BookProgressError.Unreachable))
                progress.chaptersResults.addLast(twoChapters())
                repository.seed(UUID, HighlightsState.Loaded(listOf(highlight(1, fraction = 0.6f))))
                vm.reload(1)
                vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.single().chapterNumber shouldBe null

                vm.reload(2)
                progress.chaptersCalls.size shouldBe 2
                vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.single().chapterNumber shouldBe 2
            }

            val gate = CompletableDeferred<Unit>()
            val gated = GatedBookProgress(gate)
            val repository = FakeHighlightsRepository()
            repository.seed(UUID, HighlightsState.Loaded(listOf(highlight(1, fraction = 0.6f))))
            val collecting = CollectingScope()
            try {
                val vm = HighlightsViewModel(UUID, repository, gated, TimeSource { 1_000L })
                collecting.collect(vm.uiState)
                vm.reload(1)
                vm.reload(2)
                gated.chapterCalls.size shouldBe 1

                gate.complete(Unit)
                vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.single().chapterNumber shouldBe 2
                gated.chapterCalls.size shouldBe 1
            } finally {
                collecting.cancel()
            }
        }

        it("TC-32: a retry from Failed shows Loading while it runs, then the list; chapters load only once") {
            withHarness {
                repository.seed(UUID, HighlightsState.Failed)
                val gate = CompletableDeferred<Unit>()
                repository.refreshGates.addLast(gate)
                repository.refreshResults.addLast(HighlightsState.Loaded(listOf(highlight(1))))

                vm.retry()
                repository.refreshCalls shouldBe listOf(UUID)
                vm.uiState.value.content shouldBe HighlightsUIContent.Loading

                gate.complete(Unit)
                vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.map { it.id } shouldBe listOf(1L)
                progress.chaptersCalls.size shouldBe 1

                vm.retry()
                repository.refreshCalls.size shouldBe 2
                progress.chaptersCalls.size shouldBe 1
            }
        }

        it("TC-33: a failed refresh keeps a loaded list") {
            withHarness {
                repository.seed(UUID, HighlightsState.Loaded(listOf(highlight(1), highlight(2))))
                repository.refreshResults.addLast(HighlightsState.Failed)
                vm.reload(1)
                vm.uiState.value.content.shouldBeInstanceOf<HighlightsUIContent.Loaded>().rows.map { it.id } shouldBe listOf(1L, 2L)
            }
        }
    }
})
