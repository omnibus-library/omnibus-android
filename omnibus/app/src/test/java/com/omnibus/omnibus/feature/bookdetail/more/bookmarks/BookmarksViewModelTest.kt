package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import com.omnibus.omnibus.data.bookmarks.BookmarksState
import com.omnibus.omnibus.data.bookmarks.TestBookmarksRepository
import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private const val TEXT_POSITION = "epubcfi(/6/14!/4/2)"

/** The sheet's state is shared only while collected, so a test observes it inside [block]. */
private inline fun BookmarksViewModel.whileCollected(block: () -> Unit) {
    val collecting = CollectingScope()
    collecting.collect(uiState)
    try {
        block()
    } finally {
        collecting.cancel()
    }
}

private fun BookmarksViewModel.rows(): List<BookmarkRowModel> =
    (uiState.value.content as BookmarksContent.Loaded).rows

class BookmarksViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        repository: TestBookmarksRepository = TestBookmarksRepository(),
        progress: TestBookProgressRepository = TestBookProgressRepository(),
    ) = BookmarksViewModel(bookUuid, repository, progress, TimeSource { randomLong() })

    fun TestBookmarksRepository.loaded(uuid: String, vararg positions: String) {
        seed(uuid, BookmarksState.Loaded(positions.map { randomBookmark().copy(position = it, fraction = 0.47f) }))
    }

    describe("opening the sheet") {
        context("when it has not been opened") {
            it("#117 TC-13: reads no bookmarks") {
                val repository = TestBookmarksRepository()

                sut(repository = repository).whileCollected {
                    repository.flowRequests shouldBe emptyList()
                }
            }
        }

        it("#117 TC-8, TC-38, TC-41: maps each repository state to its content") {
            checkAll(
                Exhaustive.of(
                    BookmarksState.Loading to BookmarksContent.Loading,
                    BookmarksState.Failed to BookmarksContent.Failed,
                    BookmarksState.Loaded(emptyList()) to BookmarksContent.Loaded(emptyList()),
                ),
            ) { (state, content) ->
                val uuid = randomString()
                val repository = TestBookmarksRepository().apply { seed(uuid, state) }
                val vm = sut(uuid, repository)

                vm.whileCollected {
                    vm.openSheet()

                    vm.uiState.value.content shouldBe content
                }
            }
        }

        context("when it is opened again") {
            it("#117 TC-14: re-reads the bookmarks") {
                val repository = TestBookmarksRepository()
                val vm = sut(repository = repository)

                vm.whileCollected {
                    vm.openSheet()
                    vm.closeSheet()
                    vm.openSheet()

                    repository.flowRequests.size shouldBe 2
                }
            }
        }
    }

    describe("closing the sheet") {
        it("#117 TC-11: stops showing the bookmarks") {
            val uuid = randomString()
            val repository = TestBookmarksRepository().apply { loaded(uuid, TEXT_POSITION) }
            val vm = sut(uuid, repository)

            vm.whileCollected {
                vm.openSheet()
                vm.closeSheet()

                vm.uiState.value.sheetOpen shouldBe false
                vm.uiState.value.content shouldBe BookmarksContent.Loading
            }
        }
    }

    describe("building the rows") {
        it("#117 TC-12: keeps the repository's order") {
            val uuid = randomString()
            val bookmarks = List(3) { randomBookmark() }
            val repository = TestBookmarksRepository().apply { seed(uuid, BookmarksState.Loaded(bookmarks)) }
            val vm = sut(uuid, repository)

            vm.whileCollected {
                vm.openSheet()

                vm.rows().map { it.bookmark } shouldBe bookmarks
            }
        }

        it("#117 TC-12: dates each row from the server's seconds") {
            val uuid = randomString()
            val seconds = randomLong(1L..2_000_000_000L)
            val dated = randomBookmark().copy(createdAtSeconds = seconds)
            val undated = randomBookmark().copy(createdAtSeconds = null)
            val repository = TestBookmarksRepository().apply { seed(uuid, BookmarksState.Loaded(listOf(dated, undated))) }
            val vm = sut(uuid, repository)

            vm.whileCollected {
                vm.openSheet()

                vm.rows().map { it.createdAtMillis } shouldBe listOf(seconds * 1_000L, null)
            }
        }

        it("#117 TC-12: gives each row its destination in this book") {
            val uuid = randomString()
            val repository = TestBookmarksRepository().apply { loaded(uuid, TEXT_POSITION) }
            val vm = sut(uuid, repository)

            vm.whileCollected {
                vm.openSheet()

                vm.rows().single().destination shouldBe BookDetailDestination.Reader(uuid, TEXT_POSITION)
            }
        }

        context("when bookmark ids are not unique") {
            it("#117 TC-17: still gives every row a unique key") {
                val uuid = randomString()
                val bookmarks = listOf(null, null, 5L, 5L).map { randomBookmark().copy(id = it, clientId = null) }
                val repository = TestBookmarksRepository().apply { seed(uuid, BookmarksState.Loaded(bookmarks)) }
                val vm = sut(uuid, repository)

                vm.whileCollected {
                    vm.openSheet()

                    vm.rows().map { it.key }.toSet().size shouldBe 4
                }
            }
        }

        context("when the chapters fail to load") {
            it("#117 TC-32: shows the rows without a chapter number") {
                val uuid = randomString()
                val repository = TestBookmarksRepository().apply { loaded(uuid, TEXT_POSITION) }
                val progress = TestBookProgressRepository().apply {
                    chaptersResults.add(BookChaptersResult.Failed(BookProgressError.NoServer))
                }
                val vm = sut(uuid, repository, progress)

                vm.whileCollected {
                    vm.openSheet()

                    vm.rows().single().placement shouldBe BookmarkPlacement(null, 47)
                }
            }
        }
    }

    describe("retrying") {
        it("#117 TC-42: refreshes this book's bookmarks") {
            val uuid = randomString()
            val repository = TestBookmarksRepository().apply { seed(uuid, BookmarksState.Failed) }
            val vm = sut(uuid, repository)

            vm.whileCollected {
                vm.openSheet()
                vm.retry()

                repository.refreshCalls shouldBe listOf(uuid)
            }
        }

        context("when the chapters are missing") {
            it("#117 TC-32, TC-33: loads them, so the rows gain their chapter numbers") {
                val uuid = randomString()
                val repository = TestBookmarksRepository().apply { loaded(uuid, TEXT_POSITION) }
                val progress = TestBookProgressRepository().apply {
                    chaptersResults.add(BookChaptersResult.Failed(BookProgressError.NoServer))
                    chaptersResults.add(
                        BookChaptersResult.Success(
                            ebookChapters = listOf(0f, 0.45f, 0.5f).mapIndexed { index, start ->
                                BookChapter(index, randomString(), start)
                            },
                            audioChapters = emptyList(),
                            audioFileId = randomLong(),
                            audioTotalSeconds = randomLong(),
                        ),
                    )
                }
                val vm = sut(uuid, repository, progress)

                vm.whileCollected {
                    vm.openSheet()
                    vm.retry()

                    vm.rows().single().placement shouldBe BookmarkPlacement(2, 47)
                }
            }
        }
    }
})
