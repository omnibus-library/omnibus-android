package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatAudioResult
import com.omnibus.omnibus.data.audiobook.crossformat.TestCrossFormatRepository
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.data.audiobook.position.TestListeningPositionSync
import com.omnibus.omnibus.data.audiobook.position.LatePosition
import com.omnibus.omnibus.data.audiobook.position.ListeningPositionSync
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Records the controller's commands at the moment [forceSave] runs, to prove a seek came before the save (I-SAVE-3).
 * [TestListeningPositionSync] only counts its saves.
 */
private class OrderingSync(
    private val inner: TestListeningPositionSync,
    private val controller: TestAudiobookController,
) : ListeningPositionSync {
    override val lateServerPosition: StateFlow<LatePosition?> get() = inner.lateServerPosition

    val commandsAtSave = mutableListOf<List<Command>>()

    override suspend fun forceSave() {
        commandsAtSave += controller.commands.toList()
        inner.forceSave()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultResumeOffersTest : DescribeSpec({

    lateinit var scope: CoroutineScope
    lateinit var controller: TestAudiobookController
    lateinit var sync: TestListeningPositionSync
    lateinit var ordering: OrderingSync
    lateinit var repo: TestCrossFormatRepository
    lateinit var holder: DefaultResumeOffers

    /** A settled state for the default book (book-1 at file 11) at [positionSeconds]. */
    fun settled(positionSeconds: Double = 0.0) = playbackState(positionSeconds = positionSeconds, isLoading = false)

    fun candidate(
        position: Double = 65.0,
        file: Long? = 11L,
        clock: Long = 500L,
        ahead: Boolean = true,
        follow: Boolean = false,
    ) = CrossFormatAudioResult.Candidate(
        positionSeconds = position,
        bookFileId = file,
        sourceAhead = ahead,
        sourceClock = clock,
        follow = follow,
    )

    fun ebookOffer(position: Double = 65.0, clock: Long = 500L) = EbookOffer(
        bookUuid = "book-1",
        bookFileId = 11L,
        positionSeconds = position,
        sourceAhead = true,
        sourceClock = clock,
    )

    val deviceOffer = LatePosition("book-1", 11L, 3725.0)

    beforeEach {
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        controller = TestAudiobookController(settled())
        sync = TestListeningPositionSync()
        ordering = OrderingSync(sync, controller)
        repo = TestCrossFormatRepository()
        holder = DefaultResumeOffers(dagger.Lazy<AudiobookController> { controller }, ordering, repo, scope)
    }

    afterEach {
        scope.cancel()
    }

    describe("the ebook fetch on settle") {

        // R4, I-RES-7: one read per settle
        it("#166 TC-34: a settle asks for the audio candidate exactly once (I-RES-7)") {
            holder.onSettled(controller.state.value, 0.0)

            repo.audioResumeCalls shouldBe listOf("book-1")
        }

        // R4: no book, no read and no offer
        it("#166 TC-35: a settle with no book reads nothing and offers nothing (I-RES-7)") {
            controller.state.value = playbackState(book = null)

            holder.onSettled(controller.state.value, 0.0)

            repo.audioResumeCalls shouldBe emptyList()
            holder.getOffersFlow().first() shouldBe VisibleOffers()
        }

        // I-RES-7, P-SYNC-7: a matching candidate is offered as the ebook offer, with no seek or save
        it("#166 TC-36: a matching candidate is the ebook offer, with no seek and no save (I-RES-7, P-SYNC-7)") {
            repo.result = candidate()

            holder.onSettled(controller.state.value, 0.0)

            holder.getOffersFlow().first().ebook shouldBe ebookOffer()
            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // I-RES-7: no candidate means nothing, silently
        it("#166 TC-37: a NoCandidate result offers nothing, seeks nothing and saves nothing (I-RES-7)") {
            repo.result = CrossFormatAudioResult.NoCandidate

            holder.onSettled(controller.state.value, 0.0)

            holder.getOffersFlow().first().ebook shouldBe null
            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // I-RES-7: a failed read offers nothing and throws nothing
        it("#166 TC-38: a Failed result offers nothing, seeks nothing and saves nothing (I-RES-7)") {
            repo.result = CrossFormatAudioResult.Failed

            holder.onSettled(controller.state.value, 0.0)

            holder.getOffersFlow().first().ebook shouldBe null
            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // I-RES-7: a candidate for another file, or with no file, never matches
        it("#166 TC-39: a candidate for another file, or with a null file id, is not offered (I-RES-7)") {
            listOf(candidate(file = 12L), candidate(file = null), candidate(file = null, follow = true)).forEach { result ->
                withClue("$result") {
                    repo.result = result

                    holder.onSettled(controller.state.value, 0.0)

                    holder.getOffersFlow().first().ebook shouldBe null
                    controller.commands shouldBe emptyList()
                }
            }
        }

        // P-SYNC-AC5, R5: a follow candidate seeks once, with no banner, no stored clock and no save
        it("#166 TC-40: a follow candidate seeks once and shows nothing, stores nothing, saves nothing (P-SYNC-AC5)") {
            repo.result = candidate(position = 65.0, follow = true)

            holder.onSettled(controller.state.value, 0.0)

            controller.commands shouldBe listOf(Command.SeekTo(65.0))
            holder.getOffersFlow().first().ebook shouldBe null
            repo.storedClocks shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // R5: a follow candidate for another file is not followed
        it("#166 TC-41: a follow candidate for another file or with no file does not seek (P-SYNC-AC5)") {
            listOf(candidate(file = 12L, follow = true), candidate(file = null, follow = true)).forEach { result ->
                withClue("$result") {
                    repo.result = result

                    holder.onSettled(controller.state.value, 0.0)

                    controller.commands shouldBe emptyList()
                }
            }
        }

        // P-SYNC-AC6, A2: the stored clock decides; equal hides, different shows
        it("#166 TC-42: no stored clock or a different one offers, an equal one does not (P-SYNC-AC6, A2)") {
            val rows = listOf(
                null to true,
                500L to false,
                400L to true,
            )
            rows.forEach { (dismissed, expected) ->
                withClue("dismissed=$dismissed") {
                    repo.dismissedClocks.clear()
                    if (dismissed != null) repo.dismissedClocks["book-1"] = dismissed
                    repo.result = candidate(clock = 500L)

                    holder.onSettled(controller.state.value, 0.0)

                    (holder.getOffersFlow().first().ebook != null) shouldBe expected
                }
            }
        }

        // R4: an answer that lands after the book was unloaded is dropped
        it("#166 TC-43: an answer that lands after an unload is dropped (I-RES-7)") {
            val answer = repo.hold()

            holder.onSettled(controller.state.value, 0.0)
            holder.onUnload(controller.state.value, UnloadReason.SwitchBook)
            answer.complete(candidate())

            holder.getOffersFlow().first().ebook shouldBe null
            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // R4: an answer for an earlier settle is dropped, the later one applies
        it("#166 TC-44: only the answer of the latest settle applies (I-RES-7)") {
            val first = repo.hold()
            holder.onSettled(controller.state.value, 0.0)
            val second = repo.hold()
            holder.onSettled(controller.state.value, 0.0)

            second.complete(candidate(position = 200.0, clock = 2L))
            first.complete(candidate(position = 65.0, clock = 500L))

            holder.getOffersFlow().first().ebook shouldBe ebookOffer(position = 200.0, clock = 2L)
        }

        // R4: the controller must still hold the settled book and file, not loading
        it("#166 TC-45: an answer that lands while the controller holds another book, file or is loading is dropped (I-RES-7)") {
            val base = settled()
            val variants = listOf(
                "another book" to playbackState(book = playingBook("other-book"), isLoading = false),
                "another file" to playbackState(book = playingBook(bookFileId = 12L), isLoading = false),
                "loading" to playbackState(isLoading = true),
            )
            variants.forEach { (name, variant) ->
                withClue(name) {
                    controller.state.value = base
                    val answer = repo.hold()
                    holder.onSettled(base, 0.0)

                    controller.state.value = variant
                    answer.complete(candidate(follow = true))

                    controller.state.value = base
                    holder.getOffersFlow().first().ebook shouldBe null
                    controller.commands shouldBe emptyList()
                }
            }
        }

        // R4: the epoch is re-checked after the stored clock is read
        it("#166 TC-46: an unload while the stored clock is read leaves no offer (I-RES-7)") {
            repo.dismissedClockGate = CompletableDeferred()
            repo.result = candidate()

            holder.onSettled(controller.state.value, 0.0)
            holder.onUnload(controller.state.value, UnloadReason.Stop)
            repo.dismissedClockGate?.complete(Unit)

            holder.getOffersFlow().first().ebook shouldBe null
        }

        // R4: an unload clears the offer, and an answer of the new load is still judged by its own settle
        it("#166 TC-47: an unload clears a shown offer, and an answer still pending is dropped (I-RES-7)") {
            repo.result = candidate()
            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().ebook shouldBe ebookOffer()

            holder.onUnload(controller.state.value, UnloadReason.SwitchBook)
            holder.getOffersFlow().first().ebook shouldBe null

            val late = repo.hold()
            holder.onSettled(controller.state.value, 0.0)
            holder.onUnload(controller.state.value, UnloadReason.SwitchBook)
            late.complete(candidate())

            holder.getOffersFlow().first().ebook shouldBe null
        }

        // R4: a same-book reload has no unload, so the old offer must be gone while loading and after the reset
        it("#166 TC-48: a same-book reload drops the old offer while loading and after the settle (I-RES-7)") {
            repo.result = candidate()
            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().ebook shouldBe ebookOffer()

            controller.state.value = playbackState(isLoading = true)
            holder.getOffersFlow().first().ebook shouldBe null

            controller.state.value = settled()
            repo.result = CrossFormatAudioResult.NoCandidate
            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().ebook shouldBe null
        }
    }

    describe("device offer commands") {

        // R2, I-SAVE-3, P-SYNC-6: the seek comes before the save, so the save reads the new position
        it("#166 TC-49: accepting a device offer seeks, then forces one save (I-SAVE-3, P-SYNC-6)") {
            holder.onSettled(controller.state.value, 0.0)
            sync.lateServerPosition.value = deviceOffer
            holder.getOffersFlow().first().device shouldBe deviceOffer

            holder.acceptDeviceOffer(deviceOffer)

            ordering.commandsAtSave.single() shouldBe listOf(Command.SeekTo(3725.0))
            sync.forceSaveCount shouldBe 1
            controller.commands shouldBe listOf(Command.SeekTo(3725.0))
        }

        // R2: accepting the device offer also clears the ebook offer of this load
        it("#166 TC-50: accepting a device offer hides it and clears the ebook offer for this load (R2)") {
            repo.result = candidate()
            holder.onSettled(controller.state.value, 0.0)
            sync.lateServerPosition.value = deviceOffer
            holder.getOffersFlow().first() shouldBe VisibleOffers(device = deviceOffer, ebook = ebookOffer())

            holder.acceptDeviceOffer(deviceOffer)

            holder.getOffersFlow().first() shouldBe VisibleOffers()
        }

        // R2: the controller holds another book or file, so nothing is sought or saved
        it("#166 TC-51: accepting a device offer for another book or file seeks and saves nothing (I-SAVE-3)") {
            listOf(
                playbackState(book = playingBook("other-book"), isLoading = false),
                playbackState(book = playingBook(bookFileId = 12L), isLoading = false),
            ).forEach { other ->
                controller.state.value = other
                holder.acceptDeviceOffer(deviceOffer)
            }

            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // R3, P-SYNC-6: a dismissed device offer stays hidden, even when the same value comes again
        it("#166 TC-52: a dismissed device offer stays hidden when the same position arrives again (R3)") {
            sync.lateServerPosition.value = deviceOffer
            holder.dismissDeviceOffer(deviceOffer)
            holder.getOffersFlow().first().device shouldBe null

            sync.lateServerPosition.value = null
            sync.lateServerPosition.value = deviceOffer

            holder.getOffersFlow().first().device shouldBe null
            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // R3: the dismissal is per load, so the next settle or an unload shows it again
        it("#166 TC-53: a dismissed device offer returns on the next settle and after an unload (R3)") {
            sync.lateServerPosition.value = deviceOffer
            holder.dismissDeviceOffer(deviceOffer)
            holder.getOffersFlow().first().device shouldBe null

            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().device shouldBe deviceOffer

            holder.dismissDeviceOffer(deviceOffer)
            holder.onUnload(controller.state.value, UnloadReason.Stop)
            holder.getOffersFlow().first().device shouldBe deviceOffer
        }
    }

    describe("ebook offer commands") {

        // R7, I-SAVE-3, P-SYNC-7: the seek comes before the save, and the clock is remembered
        it("#166 TC-54: accepting an ebook offer seeks, then forces one save, stores the clock and clears the offer (I-SAVE-3, P-SYNC-7)") {
            repo.result = candidate(position = 65.0, clock = 500L)
            holder.onSettled(controller.state.value, 0.0)
            val offer = ebookOffer()
            holder.getOffersFlow().first().ebook shouldBe offer

            holder.acceptEbookOffer(offer)

            ordering.commandsAtSave.single() shouldBe listOf(Command.SeekTo(65.0))
            sync.forceSaveCount shouldBe 1
            repo.storedClocks shouldBe listOf("book-1" to 500L)
            holder.getOffersFlow().first().ebook shouldBe null
        }

        // R7: another file is held, so nothing is sought or saved, but the clock is still remembered
        it("#166 TC-55: accepting an ebook offer for another file seeks and saves nothing, and still stores the clock (R7)") {
            repo.result = candidate(clock = 500L)
            holder.onSettled(controller.state.value, 0.0)
            val offer = ebookOffer(clock = 500L)
            controller.state.value = playbackState(book = playingBook(bookFileId = 12L), isLoading = false)

            holder.acceptEbookOffer(offer)

            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
            repo.storedClocks shouldBe listOf("book-1" to 500L)
        }

        // R8: dismissing remembers the clock, and moves nothing
        it("#166 TC-56: dismissing an ebook offer stores its clock and moves nothing (R8)") {
            repo.result = candidate(clock = 500L)
            holder.onSettled(controller.state.value, 0.0)
            val offer = ebookOffer(clock = 500L)

            holder.dismissEbookOffer(offer)

            repo.storedClocks shouldBe listOf("book-1" to 500L)
            holder.getOffersFlow().first().ebook shouldBe null
            controller.commands shouldBe emptyList()
            sync.forceSaveCount shouldBe 0
        }

        // R8, P-SYNC-AC6: a dismissed clock stays dismissed, and a changed clock returns
        it("#166 TC-57: a dismissed clock stays hidden on a fresh settle, and a changed one is offered again (P-SYNC-AC6)") {
            repo.result = candidate(clock = 500L)
            holder.onSettled(controller.state.value, 0.0)
            holder.dismissEbookOffer(ebookOffer(clock = 500L))

            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().ebook shouldBe null

            repo.result = candidate(position = 70.0, clock = 600L)
            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().ebook shouldBe ebookOffer(position = 70.0, clock = 600L)
        }

        // R9: a held-back ebook offer stays in the flow when the device offer is dismissed
        it("#166 TC-58: dismissing the device offer leaves the ebook offer in the flow (R9)") {
            repo.result = candidate()
            holder.onSettled(controller.state.value, 0.0)
            sync.lateServerPosition.value = deviceOffer

            holder.dismissDeviceOffer(deviceOffer)

            holder.getOffersFlow().first() shouldBe VisibleOffers(ebook = ebookOffer())
        }
    }

    describe("the device offer's timing") {

        // I-RES-6, P-SYNC-6: the late position may arrive before or after the settle, and playing past it hides it
        it("#166 TC-59: a late position is offered whether it arrives before or after the settle, and hides once passed (I-RES-6)") {
            sync.lateServerPosition.value = deviceOffer
            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().device shouldBe deviceOffer

            sync.lateServerPosition.value = null
            holder.onSettled(controller.state.value, 0.0)
            holder.getOffersFlow().first().device shouldBe null
            sync.lateServerPosition.value = deviceOffer
            holder.getOffersFlow().first().device shouldBe deviceOffer

            sync.lateServerPosition.value = null
            holder.getOffersFlow().first().device shouldBe null

            sync.lateServerPosition.value = deviceOffer
            controller.state.value = playbackState(positionSeconds = 3726.0)
            holder.getOffersFlow().first().device shouldBe null
        }

        // I-RES-6 (startup): the controller is read only when the holder is used, never in its constructor
        it("#166 TC-60: building the holder does not touch the controller (I-RES-6)") {
            var reads = 0
            val lazyController = dagger.Lazy<AudiobookController> {
                reads++
                controller
            }

            val fresh = DefaultResumeOffers(lazyController, ordering, repo, scope)

            reads shouldBe 0
            fresh.getOffersFlow()
            reads shouldBe 1
        }
    }
})
