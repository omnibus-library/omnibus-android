package com.omnibus.omnibus.data.highlights

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private val oneHighlight = Highlight(
    id = 1L,
    text = "a",
    note = null,
    color = HighlightColor.Amber,
    location = "epubcfi(/6/4)",
    format = "epub",
    createdAtMillis = null,
)

class FakeHighlightsRepositoryTest : DescribeSpec({

    describe("the fake highlights repository") {

        it("TC-18: an unseeded book is Loading, a seeded book is its state, and refreshes are recorded in order") {
            runTest {
                val fake = FakeHighlightsRepository()
                fake.getHighlightsFlow("A").first() shouldBe HighlightsState.Loading

                fake.seed("B", HighlightsState.Loaded(emptyList()))
                fake.getHighlightsFlow("B").first() shouldBe HighlightsState.Loaded(emptyList())

                fake.refreshResults.addLast(HighlightsState.Loaded(listOf(oneHighlight)))
                fake.refreshResults.addLast(HighlightsState.Failed)
                fake.refreshResults.addLast(HighlightsState.Loaded(emptyList()))

                fake.refresh("A")
                fake.getHighlightsFlow("A").first() shouldBe HighlightsState.Loaded(listOf(oneHighlight))
                fake.refresh("B")
                fake.refresh("A")

                fake.refreshCalls shouldBe listOf("A", "B", "A")
                fake.getHighlightsFlow("A").first() shouldBe HighlightsState.Loaded(emptyList())
                fake.getHighlightsFlow("B").first() shouldBe HighlightsState.Loaded(emptyList())
            }
        }

        it("TC-19: the fake keeps a loaded list over a failure, shows Loading while gated, and leaves Failed when nothing is scripted") {
            runTest {
                // A loaded list survives a scripted failure.
                val kept = FakeHighlightsRepository()
                kept.seed("A", HighlightsState.Loaded(listOf(oneHighlight)))
                kept.refreshResults.addLast(HighlightsState.Failed)
                kept.refresh("A")
                kept.getHighlightsFlow("A").first() shouldBe HighlightsState.Loaded(listOf(oneHighlight))

                // A refresh from Failed is Loading while gated, then the scripted list.
                val gated = FakeHighlightsRepository()
                gated.seed("A", HighlightsState.Failed)
                val gate = CompletableDeferred<Unit>()
                gated.refreshGates.addLast(gate)
                gated.refreshResults.addLast(HighlightsState.Loaded(listOf(oneHighlight)))
                val refresh = launch(UnconfinedTestDispatcher(testScheduler)) { gated.refresh("A") }
                gated.getHighlightsFlow("A").first() shouldBe HighlightsState.Loading
                gate.complete(Unit)
                refresh.join()
                gated.getHighlightsFlow("A").first() shouldBe HighlightsState.Loaded(listOf(oneHighlight))

                // Failed with nothing scripted stays Failed, and nothing is thrown.
                val empty = FakeHighlightsRepository()
                empty.seed("A", HighlightsState.Failed)
                empty.refresh("A")
                empty.getHighlightsFlow("A").first() shouldBe HighlightsState.Failed
            }
        }
    }
})
