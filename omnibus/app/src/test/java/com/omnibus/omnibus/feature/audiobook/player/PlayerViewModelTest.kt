package com.omnibus.omnibus.feature.audiobook.player

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.data.audiobook.playback.randomPlaybackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.TestBookAvailability
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A state holding the book with [bookUuid], and random otherwise. */
private fun holding(bookUuid: String): PlaybackState =
    randomPlaybackState().copy(book = randomPlayingBook().copy(bookUuid = bookUuid))

private fun TestAudiobookController.loads() = commands.filterIsInstance<Command.Load>()

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        controller: TestAudiobookController = TestAudiobookController(),
        availability: TestBookAvailability = TestBookAvailability(),
        library: TestLibraryRepository = TestLibraryRepository(),
        fileId: Long? = randomLong(),
        position: Double? = randomDouble(0.0, 10_000.0),
    ) = PlayerViewModel(bookUuid, fileId, position, controller, availability, library)

    describe("the offline gate") {
        context("when the book can't be opened") {
            // P-SCR-2, P-SCR-AC6, AND-RUSE-7
            it("#165 TC-11: never loads (P-SCR-2)") {
                val controller = TestAudiobookController()

                sut(controller = controller, availability = TestBookAvailability(answer = false))

                controller.loads().shouldBeEmpty()
            }

            context("when the controller holds the book") {
                // P-SCR-2 (exemption for a book already loaded)
                it("#165 TC-12: opens the gate") {
                    val uuid = randomString()
                    val controller = TestAudiobookController(holding(uuid))

                    val vm = sut(uuid, controller, TestBookAvailability(answer = false))

                    vm.uiState.value.gate shouldBe PlayerGate.Open
                }
            }

            context("when the controller holds another book") {
                // P-SCR-2 (exemption is per book)
                it("#165 TC-13: shows the offline gate") {
                    val uuid = randomString()
                    val controller = TestAudiobookController(holding("$uuid-other"))

                    val vm = sut(uuid, controller, TestBookAvailability(answer = false))

                    vm.uiState.value.gate shouldBe PlayerGate.Offline
                }
            }
        }

        // P-SCR-2, P-SCR-AC6, AND-RUSE-7
        it("#165 TC-11: asks whether the book can be opened for listening (P-SCR-2)") {
            val uuid = randomString()
            val availability = TestBookAvailability()

            sut(uuid, availability = availability)

            availability.calls shouldBe listOf(uuid to ProgressFormat.Listening)
        }
    }

    describe("loading") {
        // P-SCR-1, P-SYNC-2, C2
        it("#165 TC-17: loads once with autoplay from the route's arguments (P-SCR-1)") {
            val uuid = randomString()
            val fileId = randomLong()
            val position = randomDouble(0.0, 10_000.0)
            val controller = TestAudiobookController()

            sut(uuid, controller, TestBookAvailability(answer = true), fileId = fileId, position = position)

            controller.commands shouldBe listOf(Command.Load(uuid, fileId, position, autoplay = true))
        }

        // R4
        it("#165 TC-18: never reloads on a state update") {
            val uuid = randomString()
            val controller = TestAudiobookController()
            sut(uuid, controller, TestBookAvailability(answer = true))

            controller.state.value = holding(uuid)
            controller.state.value = holding(uuid)

            controller.loads() shouldHaveSize 1
        }
    }

    describe("the library book") {
        // R8, P-SCR-6
        it("#165 TC-19: gives the title until the controller knows it") {
            val library = TestLibraryRepository()
            val held = CompletableDeferred<BookResult>()
            library.pendingBook = held
            val vm = sut(library = library)
            val book = randomBook()

            held.complete(BookResult.Success(book))

            vm.uiState.value.title shouldBe book.displayTitle
        }
    }

    describe("leaving") {
        context("when the controller had the book") {
            // R20
            it("#165 TC-20 TC-21: leaves once the controller drops it or loads another book") {
                val uuid = randomString()

                checkAll(Exhaustive.of(PlaybackState(), holding("$uuid-other"))) { next ->
                    val controller = TestAudiobookController()
                    val vm = sut(uuid, controller)
                    controller.state.value = holding(uuid)

                    controller.state.value = next

                    vm.uiState.value.leave shouldBe true
                }
            }
        }

        context("when the load is still landing") {
            // R20
            it("#165 TC-22: stays") {
                val uuid = randomString()
                val controller = TestAudiobookController(holding("$uuid-other"))
                val vm = sut(uuid, controller)

                controller.state.value = PlaybackState()

                vm.uiState.value.leave shouldBe false
            }
        }
    }

    describe("the user's intents") {
        // P-PLAY-3-5, C2, I-HLTH-3
        it("#165 TC-24 TC-83: each intent sends its command") {
            val seek = randomDouble(0.0, 10_000.0)

            checkAll(
                Exhaustive.of<Pair<(PlayerViewModel) -> Unit, Command>>(
                    PlayerViewModel::onPlayPauseToggled to Command.TogglePlayPause,
                    PlayerViewModel::onSkipBack to Command.SkipBack,
                    PlayerViewModel::onSkipForward to Command.SkipForward,
                    PlayerViewModel::onPreviousChapter to Command.PreviousChapter,
                    PlayerViewModel::onNextChapter to Command.NextChapter,
                    { vm: PlayerViewModel -> vm.onSeek(seek) } to Command.SeekTo(seek),
                    PlayerViewModel::onRetry to Command.Play,
                ),
            ) { (intent, command) ->
                val controller = TestAudiobookController()
                val vm = sut(controller = controller)
                controller.commands.clear()

                intent(vm)

                controller.commands shouldBe listOf(command)
            }
        }
    }
})
