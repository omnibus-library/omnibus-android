package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelHighlightsTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun savedAt(cfi: String, color: HighlightColor, note: String? = null): Highlight =
        randomHighlight().copy(location = cfi, color = color, note = note)

    describe("re-reading the book's highlights on open") {
        context("when the repository already holds the book's highlights") {
            it("#54 TC-19: refreshes the book's highlights once for each ViewModel") {
                val uuid = randomString()
                val highlights = TestHighlightsRepository().apply {
                    seed(uuid, HighlightsState.Loaded(listOf(randomHighlight())))
                }

                sut(bookUuid = uuid, highlightsRepository = highlights)
                sut(bookUuid = uuid, highlightsRepository = highlights)

                highlights.refreshCalls shouldBe listOf(uuid, uuid)
            }
        }
    }

    describe("the highlights the page draws from the book's saved highlights") {
        context("when the book's highlights are loaded") {
            it("#54 TC-20: draws only the EPUB rows, in their colour, with a note flag for a note") {
                val uuid = randomString()
                val cfi = randomCfi()
                val otherCfi = randomCfi()
                val withNote = savedAt(cfi, HighlightColor.Amber, note = randomString())
                val noNote = savedAt(otherCfi, HighlightColor.Green)
                val pdf = savedAt("pdf:${randomString()}", HighlightColor.Blue, note = randomString())
                val highlights = TestHighlightsRepository().apply {
                    seed(uuid, HighlightsState.Loaded(listOf(withNote, noNote, pdf)))
                }

                val vm = sut(bookUuid = uuid, highlightsRepository = highlights)

                vm.uiState.value.pageHighlights shouldBe listOf(
                    PageHighlight(cfi, HighlightColor.Amber, hasNote = true),
                    PageHighlight(otherCfi, HighlightColor.Green, hasNote = false),
                )
            }

            it("#54 TC-20: draws a row added to the list") {
                val uuid = randomString()
                val firstCfi = randomCfi()
                val addedCfi = randomCfi()
                val first = savedAt(firstCfi, HighlightColor.Amber)
                val added = savedAt(addedCfi, HighlightColor.Rose, note = randomString())
                val highlights = TestHighlightsRepository().apply {
                    seed(uuid, HighlightsState.Loaded(listOf(first)))
                }
                val vm = sut(bookUuid = uuid, highlightsRepository = highlights)

                highlights.seed(uuid, HighlightsState.Loaded(listOf(first, added)))

                vm.uiState.value.pageHighlights shouldBe listOf(
                    PageHighlight(firstCfi, HighlightColor.Amber, hasNote = false),
                    PageHighlight(addedCfi, HighlightColor.Rose, hasNote = true),
                )
            }

            it("#54 TC-20: draws a row recoloured in the list in its new colour") {
                val uuid = randomString()
                val cfi = randomCfi()
                val first = savedAt(cfi, HighlightColor.Amber)
                val highlights = TestHighlightsRepository().apply {
                    seed(uuid, HighlightsState.Loaded(listOf(first)))
                }
                val vm = sut(bookUuid = uuid, highlightsRepository = highlights)

                highlights.seed(uuid, HighlightsState.Loaded(listOf(first.copy(color = HighlightColor.Violet))))

                vm.uiState.value.pageHighlights shouldBe
                    listOf(PageHighlight(cfi, HighlightColor.Violet, hasNote = false))
            }

            it("#54 TC-20: stops drawing a row removed from the list") {
                val uuid = randomString()
                val keptCfi = randomCfi()
                val kept = savedAt(keptCfi, HighlightColor.Green)
                val removed = savedAt(randomCfi(), HighlightColor.Blue)
                val highlights = TestHighlightsRepository().apply {
                    seed(uuid, HighlightsState.Loaded(listOf(kept, removed)))
                }
                val vm = sut(bookUuid = uuid, highlightsRepository = highlights)

                highlights.seed(uuid, HighlightsState.Loaded(listOf(kept)))

                vm.uiState.value.pageHighlights shouldBe
                    listOf(PageHighlight(keptCfi, HighlightColor.Green, hasNote = false))
            }
        }

        context("when the highlights read has failed") {
            it("#54 TC-20: draws nothing") {
                val uuid = randomString()
                val highlights = TestHighlightsRepository().apply { seed(uuid, HighlightsState.Failed) }

                val vm = sut(bookUuid = uuid, highlightsRepository = highlights)

                vm.uiState.value.pageHighlights.shouldBeEmpty()
            }

            it("#54 TC-20: leaves the load status and the book as they are without the failure") {
                val uuid = randomString()
                val failed = TestHighlightsRepository().apply { seed(uuid, HighlightsState.Failed) }
                val quiet = TestHighlightsRepository().apply { seed(uuid, HighlightsState.Loading) }

                val failedVm = sut(bookUuid = uuid, highlightsRepository = failed)
                val quietVm = sut(bookUuid = uuid, highlightsRepository = quiet)

                failedVm.uiState.value.loadStatus shouldBe quietVm.uiState.value.loadStatus
                failedVm.uiState.value.book shouldBe quietVm.uiState.value.book
            }
        }

        context("when the highlights read is still loading") {
            it("#54 TC-20: draws nothing") {
                val uuid = randomString()
                val highlights = TestHighlightsRepository().apply { seed(uuid, HighlightsState.Loading) }

                val vm = sut(bookUuid = uuid, highlightsRepository = highlights)

                vm.uiState.value.pageHighlights.shouldBeEmpty()
            }
        }
    }
})
