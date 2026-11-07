package com.omnibus.omnibus.data.audiobook.offers

import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatAudioResult
import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatRepository
import com.omnibus.omnibus.data.audiobook.crossformat.TestCrossFormatRepository
import com.omnibus.omnibus.data.audiobook.crossformat.randomCandidate
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.position.LatePosition
import com.omnibus.omnibus.data.audiobook.position.ListeningPositionSync
import com.omnibus.omnibus.data.audiobook.position.TestListeningPositionSync
import com.omnibus.omnibus.data.audiobook.position.randomLatePosition
import com.omnibus.omnibus.data.audiobook.position.randomPlayingBook
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomEnum
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Records the controller's commands at the moment [forceSave] runs, to prove a seek came before the save (I-SAVE-3). */
private class OrderingSync(private val controller: TestAudiobookController) : ListeningPositionSync {
    private val inner = TestListeningPositionSync()

    override val lateServerPosition: StateFlow<LatePosition?> get() = inner.lateServerPosition

    val commandsAtSave = mutableListOf<List<Command>>()

    override suspend fun forceSave() {
        commandsAtSave += controller.commands.toList()
    }
}

/** Runs [block] on a scope that starts each launch at once, cancelled afterwards. */
@OptIn(ExperimentalCoroutinesApi::class)
private inline fun withScope(block: (CoroutineScope) -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    try {
        block(scope)
    } finally {
        scope.cancel()
    }
}

private fun controllerHolding(book: PlayingBook) = TestAudiobookController(playbackState(book))

/** A candidate for [book]'s file, to offer rather than to follow. */
private fun offerableCandidate(book: PlayingBook) = randomCandidate().copy(bookFileId = book.bookFileId, follow = false)

private fun CrossFormatAudioResult.Candidate.asOffer(book: PlayingBook) = EbookOffer(
    bookUuid = book.bookUuid,
    bookFileId = book.bookFileId,
    positionSeconds = positionSeconds,
    sourceAhead = sourceAhead,
    sourceClock = sourceClock,
)

/** A late position for [book], more than 1 s past the start of the book. */
private fun lateFor(book: PlayingBook) = randomLatePosition().copy(
    bookUuid = book.bookUuid,
    bookFileId = book.bookFileId,
    positionSeconds = randomDouble(2.0, 3_000.0),
)

private suspend fun ResumeOffers.visible(): VisibleOffers = getOffersFlow().first()

private fun DefaultResumeOffers.settle(controller: TestAudiobookController) =
    onSettled(controller.state.value, randomDouble())

class DefaultResumeOffersTest : DescribeSpec({

    fun sut(
        scope: CoroutineScope,
        controller: TestAudiobookController = TestAudiobookController(),
        sync: ListeningPositionSync = TestListeningPositionSync(),
        repository: CrossFormatRepository = TestCrossFormatRepository(),
    ) = DefaultResumeOffers(dagger.Lazy<AudiobookController> { controller }, sync, repository, scope)

    describe("the ebook offer of a settled load") {

        context("when the candidate matches the loaded file") {
            // I-RES-7, P-SYNC-7
            it("#166 TC-36: offers it (I-RES-7, P-SYNC-7)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val candidate = offerableCandidate(book)
                    val holder = sut(scope, controller, repository = TestCrossFormatRepository(candidate))

                    holder.settle(controller)

                    holder.visible().ebook shouldBe candidate.asOffer(book)
                }
            }
        }

        // I-RES-7
        it("#166 TC-37, TC-38: offers nothing for each answer without a candidate (I-RES-7)") {
            checkAll(Exhaustive.of(CrossFormatAudioResult.NoCandidate, CrossFormatAudioResult.Failed)) { result ->
                withScope { scope ->
                    val controller = controllerHolding(randomPlayingBook())
                    val holder = sut(scope, controller, repository = TestCrossFormatRepository(result))

                    holder.settle(controller)

                    holder.visible().ebook.shouldBeNull()
                }
            }
        }

        // I-RES-7, R5: a candidate for another file, or with no file, never matches
        it("#166 TC-39, TC-41: ignores a candidate of another or an unknown file, offered or followed (I-RES-7, P-SYNC-AC5)") {
            val book = randomPlayingBook()

            checkAll(Exhaustive.of(book.bookFileId + 1, null), Exhaustive.boolean()) { file, follow ->
                withScope { scope ->
                    val controller = controllerHolding(book)
                    val candidate = offerableCandidate(book).copy(bookFileId = file, follow = follow)
                    val holder = sut(scope, controller, repository = TestCrossFormatRepository(candidate))

                    holder.settle(controller)

                    holder.visible().ebook.shouldBeNull()
                    controller.commands.shouldBeEmpty()
                }
            }
        }

        context("when the candidate is in follow mode") {
            // P-SYNC-AC5, R5
            it("#166 TC-40: seeks to it (P-SYNC-AC5)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val candidate = offerableCandidate(book).copy(follow = true)
                    val holder = sut(scope, controller, repository = TestCrossFormatRepository(candidate))

                    holder.settle(controller)

                    controller.commands shouldBe listOf(Command.SeekTo(candidate.positionSeconds))
                }
            }

            // P-SYNC-AC5, R5
            it("#166 TC-40: shows no offer (P-SYNC-AC5)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val candidate = offerableCandidate(book).copy(follow = true)
                    val holder = sut(scope, controller, repository = TestCrossFormatRepository(candidate))

                    holder.settle(controller)

                    holder.visible().ebook.shouldBeNull()
                }
            }
        }

        context("when the listener dismissed a different clock") {
            // P-SYNC-AC6, A2: the offer returns when the clock changes, greater or smaller
            it("#166 TC-42: offers it again, for an older or a newer dismissed clock (P-SYNC-AC6, A2)") {
                val book = randomPlayingBook()
                val candidate = offerableCandidate(book).copy(sourceClock = 500L)

                checkAll(Exhaustive.of(499L, 501L)) { dismissed ->
                    withScope { scope ->
                        val controller = controllerHolding(book)
                        val repository = TestCrossFormatRepository(candidate)
                        repository.dismissedClocks[book.bookUuid] = dismissed
                        val holder = sut(scope, controller, repository = repository)

                        holder.settle(controller)

                        holder.visible().ebook shouldBe candidate.asOffer(book)
                    }
                }
            }
        }

        context("when the answer lands after an unload") {
            // R4
            it("#166 TC-43: drops it (I-RES-7)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val repository = TestCrossFormatRepository()
                    val answer = repository.hold()
                    val holder = sut(scope, controller, repository = repository)

                    holder.settle(controller)
                    holder.onUnload(controller.state.value, randomEnum<UnloadReason>())
                    answer.complete(offerableCandidate(book))

                    holder.visible().ebook.shouldBeNull()
                }
            }
        }

        context("when a later settle is waiting too") {
            // R4
            it("#166 TC-44: applies only the later answer (I-RES-7)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val repository = TestCrossFormatRepository()
                    val first = repository.hold()
                    val holder = sut(scope, controller, repository = repository)
                    holder.settle(controller)
                    val second = repository.hold()
                    holder.settle(controller)
                    val latest = offerableCandidate(book)

                    second.complete(latest)
                    first.complete(offerableCandidate(book))

                    holder.visible().ebook shouldBe latest.asOffer(book)
                }
            }
        }

        // R4: the controller must still hold the settled book and file, not loading
        it("#166 TC-45: drops an answer once the controller holds another book or file, or is loading (I-RES-7)") {
            val book = randomPlayingBook()

            checkAll(
                Exhaustive.of(
                    playbackState(book.copy(bookUuid = "${book.bookUuid}-other")),
                    playbackState(book.copy(bookFileId = book.bookFileId + 1)),
                    playbackState(book, isLoading = true),
                ),
            ) { moved ->
                withScope { scope ->
                    val controller = controllerHolding(book)
                    val repository = TestCrossFormatRepository()
                    val answer = repository.hold()
                    val holder = sut(scope, controller, repository = repository)
                    holder.settle(controller)

                    controller.state.value = moved
                    answer.complete(offerableCandidate(book).copy(follow = true))

                    controller.commands.shouldBeEmpty()
                }
            }
        }

        context("when an unload lands while the dismissed clock is read") {
            // R4: the load is checked again after the read
            it("#166 TC-46: offers nothing (I-RES-7)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val gate = CompletableDeferred<Unit>()
                    val repository = TestCrossFormatRepository(offerableCandidate(book))
                    repository.dismissedClockGate = gate
                    val holder = sut(scope, controller, repository = repository)

                    holder.settle(controller)
                    holder.onUnload(controller.state.value, randomEnum<UnloadReason>())
                    gate.complete(Unit)

                    holder.visible().ebook.shouldBeNull()
                }
            }
        }

        context("when an offer is showing") {

            // R4: a same-book reload has no unload, so its settle resets the offer
            it("#166 TC-48: goes on the next settle (I-RES-7)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val repository = TestCrossFormatRepository(offerableCandidate(book))
                    val holder = sut(scope, controller, repository = repository)
                    holder.settle(controller)

                    repository.result = CrossFormatAudioResult.NoCandidate
                    holder.settle(controller)

                    holder.visible().ebook.shouldBeNull()
                }
            }
        }
    }

    describe("the device offer") {

        context("when it is accepted") {
            // R2, I-SAVE-3, P-SYNC-6: the seek comes first, so the save reads the new position
            it("#166 TC-49: seeks before it saves (I-SAVE-3, P-SYNC-6)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val sync = OrderingSync(controller)
                    val late = lateFor(book)

                    sut(scope, controller, sync).acceptDeviceOffer(late)

                    sync.commandsAtSave shouldBe listOf(listOf(Command.SeekTo(late.positionSeconds)))
                }
            }

            // R2
            it("#166 TC-50: clears both offers of this load (R2)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val sync = TestListeningPositionSync()
                    val holder = sut(scope, controller, sync, TestCrossFormatRepository(offerableCandidate(book)))
                    val late = lateFor(book)
                    holder.settle(controller)
                    sync.lateServerPosition.value = late

                    holder.acceptDeviceOffer(late)

                    holder.visible() shouldBe VisibleOffers()
                }
            }

            // R2
            it("#166 TC-51: moves nothing once the controller holds another book or file (I-SAVE-3)") {
                val book = randomPlayingBook()

                checkAll(
                    Exhaustive.of(book.copy(bookUuid = "${book.bookUuid}-other"), book.copy(bookFileId = book.bookFileId + 1)),
                ) { other ->
                    withScope { scope ->
                        val controller = controllerHolding(other)

                        sut(scope, controller).acceptDeviceOffer(lateFor(book))

                        controller.commands.shouldBeEmpty()
                    }
                }
            }
        }

        context("when it is dismissed") {
            // R3, P-SYNC-6
            it("#166 TC-52: stays hidden with the same position arriving again (R3)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val sync = TestListeningPositionSync()
                    val holder = sut(scope, controllerHolding(book), sync)
                    val late = lateFor(book)
                    holder.dismissDeviceOffer(late)

                    sync.lateServerPosition.value = null
                    sync.lateServerPosition.value = late

                    holder.visible().device.shouldBeNull()
                }
            }

            // R3: the dismissal is per load
            it("#166 TC-53: returns on the next settle (R3)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val sync = TestListeningPositionSync()
                    val holder = sut(scope, controller, sync)
                    val late = lateFor(book)
                    sync.lateServerPosition.value = late
                    holder.dismissDeviceOffer(late)

                    holder.settle(controller)

                    holder.visible().device shouldBe late
                }
            }
        }
    }

    describe("answering the ebook offer") {

        context("when it is accepted") {
            // R7, I-SAVE-3, P-SYNC-7: the seek comes first, so the save reads the new position
            it("#166 TC-54: seeks before it saves (I-SAVE-3, P-SYNC-7)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val sync = OrderingSync(controller)
                    val offer = offerableCandidate(book).asOffer(book)

                    sut(scope, controller, sync).acceptEbookOffer(offer)

                    sync.commandsAtSave shouldBe listOf(listOf(Command.SeekTo(offer.positionSeconds)))
                }
            }

            context("when the controller holds another file") {
                // R7
                it("#166 TC-55: moves nothing (R7)") {
                    withScope { scope ->
                        val book = randomPlayingBook()
                        val controller = controllerHolding(book.copy(bookFileId = book.bookFileId + 1))

                        sut(scope, controller).acceptEbookOffer(offerableCandidate(book).asOffer(book))

                        controller.commands.shouldBeEmpty()
                    }
                }

                // R7: the clock is still remembered
                it("#166 TC-55: remembers the clock (R7)") {
                    withScope { scope ->
                        val book = randomPlayingBook()
                        val controller = controllerHolding(book.copy(bookFileId = book.bookFileId + 1))
                        val repository = TestCrossFormatRepository()
                        val offer = offerableCandidate(book).asOffer(book)

                        sut(scope, controller, repository = repository).acceptEbookOffer(offer)

                        repository.dismissedClocks shouldBe mapOf(book.bookUuid to offer.sourceClock)
                    }
                }
            }
        }

        context("when it is dismissed") {
            // R8
            it("#166 TC-56: hides the offer (R8)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val candidate = offerableCandidate(book)
                    val holder = sut(scope, controller, repository = TestCrossFormatRepository(candidate))
                    holder.settle(controller)

                    holder.dismissEbookOffer(candidate.asOffer(book))

                    holder.visible().ebook.shouldBeNull()
                }
            }

            // R8, P-SYNC-AC6
            it("#166 TC-57: stays hidden on a fresh settle (P-SYNC-AC6)") {
                withScope { scope ->
                    val book = randomPlayingBook()
                    val controller = controllerHolding(book)
                    val candidate = offerableCandidate(book)
                    val holder = sut(scope, controller, repository = TestCrossFormatRepository(candidate))
                    holder.settle(controller)
                    holder.dismissEbookOffer(candidate.asOffer(book))

                    holder.settle(controller)

                    holder.visible().ebook.shouldBeNull()
                }
            }
        }
    }
})
