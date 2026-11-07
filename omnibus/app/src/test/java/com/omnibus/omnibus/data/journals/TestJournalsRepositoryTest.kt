package com.omnibus.omnibus.data.journals

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private val oneEntry = JournalEntry(
    id = 1L,
    authorId = 7L,
    authorName = "Ann",
    bodyMd = "a",
    isDraft = false,
    createdAtMillis = null,
)

class TestJournalsRepositoryTest : DescribeSpec({

    describe("the fake journals repository") {

        it("TC-17: an unseeded book is Loading, a seeded book is its state, and refreshes are recorded in order") {
            runTest {
                val fake = TestJournalsRepository()
                fake.getJournalsFlow("A").first() shouldBe JournalsState.Loading

                fake.seed("B", JournalsState.Loaded(emptyList()))
                fake.getJournalsFlow("B").first() shouldBe JournalsState.Loaded(emptyList())

                fake.refreshResults.addLast(JournalsState.Loaded(listOf(oneEntry)))
                fake.refreshResults.addLast(JournalsState.Failed)
                fake.refreshResults.addLast(JournalsState.Loaded(emptyList()))

                fake.refresh("A")
                fake.getJournalsFlow("A").first() shouldBe JournalsState.Loaded(listOf(oneEntry))
                fake.refresh("B")
                fake.refresh("A")

                fake.refreshCalls shouldBe listOf("A", "B", "A")
                fake.getJournalsFlow("A").first() shouldBe JournalsState.Loaded(emptyList())
                fake.getJournalsFlow("B").first() shouldBe JournalsState.Loaded(emptyList())
            }
        }

        it("TC-17: the fake keeps a loaded list over a failure, shows Loading while gated, and stays Failed when nothing is scripted") {
            runTest {
                // A loaded list survives a scripted failure.
                val kept = TestJournalsRepository()
                kept.seed("A", JournalsState.Loaded(listOf(oneEntry)))
                kept.refreshResults.addLast(JournalsState.Failed)
                kept.refresh("A")
                kept.getJournalsFlow("A").first() shouldBe JournalsState.Loaded(listOf(oneEntry))

                // A refresh from Failed is Loading while gated, then the scripted list.
                val gated = TestJournalsRepository()
                gated.seed("A", JournalsState.Failed)
                val gate = CompletableDeferred<Unit>()
                gated.refreshGates.addLast(gate)
                gated.refreshResults.addLast(JournalsState.Loaded(listOf(oneEntry)))
                val refresh = launch(UnconfinedTestDispatcher(testScheduler)) { gated.refresh("A") }
                gated.getJournalsFlow("A").first() shouldBe JournalsState.Loading
                gate.complete(Unit)
                refresh.join()
                gated.getJournalsFlow("A").first() shouldBe JournalsState.Loaded(listOf(oneEntry))

                // Failed with nothing scripted stays Failed, and nothing is thrown.
                val empty = TestJournalsRepository()
                empty.seed("A", JournalsState.Failed)
                empty.refresh("A")
                empty.getJournalsFlow("A").first() shouldBe JournalsState.Failed
            }
        }
    }
})
