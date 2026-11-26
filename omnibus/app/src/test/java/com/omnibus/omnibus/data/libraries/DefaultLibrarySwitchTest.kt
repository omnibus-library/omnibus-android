package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.data.libraries.local.PendingOpeningStore
import com.omnibus.omnibus.data.libraries.local.TestPendingOpeningStore
import com.omnibus.omnibus.data.libraries.local.TestLibraryTokenStore
import com.omnibus.omnibus.di.AppRestarter
import com.omnibus.omnibus.di.TestAppRestarter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Three libraries: `a` active, `b` and `c` not. */
private fun threeLibraries(): List<Library> = listOf(
    randomLibrary().copy(id = "a", isActive = true),
    randomLibrary().copy(id = "b", isActive = false),
    randomLibrary().copy(id = "c", isActive = false),
)

class DefaultLibrarySwitchTest : DescribeSpec({

    fun sut(
        libraries: LibrariesRepository = TestLibrariesRepository(),
        activeLibrary: ActiveLibrary = TestActiveLibrary(),
        pendingOpening: PendingOpeningStore = TestPendingOpeningStore(),
        audiobook: AudiobookController = TestAudiobookController(),
        restarter: AppRestarter = TestAppRestarter(),
    ): DefaultLibrarySwitch = DefaultLibrarySwitch(
        libraries = libraries,
        activeLibrary = activeLibrary,
        pendingOpening = pendingOpening,
        audiobook = audiobook,
        restarter = restarter,
    )

    describe("committing a switch") {

        it("#369 TC-9: stops playback, saves the pending opening, makes the target active, then restarts, in that order") {
            runTest {
                val log = mutableListOf<String>()
                val libraries = TestLibrariesRepository(threeLibraries())
                val switch = sut(
                    libraries = object : LibrariesRepository by libraries {
                        override suspend fun setActive(libraryId: String) {
                            log += "setActive"
                            libraries.setActive(libraryId)
                        }
                    },
                    activeLibrary = object : ActiveLibrary {
                        override suspend fun id(): String? {
                            log += "activeId"
                            return "a"
                        }
                    },
                    pendingOpening = object : PendingOpeningStore by TestPendingOpeningStore() {
                        override suspend fun save(opening: PendingOpening) {
                            log += "save"
                        }
                    },
                    audiobook = object : AudiobookController by TestAudiobookController() {
                        override fun stop() {
                            log += "stop"
                        }
                    },
                    restarter = AppRestarter { log += "restart" },
                )

                switch.commit("b", SwitchKind.Switch)

                log shouldBe listOf("activeId", "stop", "save", "setActive", "restart")
            }
        }

        it("#369 TC-9: saves the pending opening for the target and the kind") {
            runTest {
                val pending = TestPendingOpeningStore()
                val switch = sut(
                    libraries = TestLibrariesRepository(threeLibraries()),
                    activeLibrary = TestActiveLibrary("a"),
                    pendingOpening = pending,
                )

                switch.commit("b", SwitchKind.Switch)

                pending.saves shouldBe listOf(PendingOpening("b", SwitchKind.Switch))
            }
        }

        it("#369 TC-9: makes the target the active library") {
            runTest {
                val libraries = TestLibrariesRepository(threeLibraries())
                val switch = sut(libraries = libraries, activeLibrary = TestActiveLibrary("a"))

                switch.commit("b", SwitchKind.Switch)

                libraries.activations shouldBe listOf("b")
            }
        }

        it("#369 TC-9: restarts the app once") {
            runTest {
                val restarter = TestAppRestarter()
                val switch = sut(
                    libraries = TestLibrariesRepository(threeLibraries()),
                    activeLibrary = TestActiveLibrary("a"),
                    restarter = restarter,
                )

                switch.commit("b", SwitchKind.Switch)

                restarter.restarts shouldBe 1
            }
        }

        it("#369 TC-9: has the pending opening saved when it restarts") {
            runTest {
                val pending = TestPendingOpeningStore()
                val restarter = TestAppRestarter()
                var savedWhenRestarted: PendingOpening? = null
                restarter.onRestart = { savedWhenRestarted = pending.opening.value }
                val switch = sut(
                    libraries = TestLibrariesRepository(threeLibraries()),
                    activeLibrary = TestActiveLibrary("a"),
                    pendingOpening = pending,
                    restarter = restarter,
                )

                switch.commit("b", SwitchKind.Switch)

                savedWhenRestarted shouldBe PendingOpening("b", SwitchKind.Switch)
            }
        }

        it("#369 TC-9: changes no library's token") {
            runTest {
                val tokens = TestLibraryTokenStore(mapOf("a" to "token-a", "b" to "token-b", "c" to "token-c"))
                val before = listOf("a", "b", "c").map { tokens.getTokenFlow(it).first() }
                val switch = sut(
                    libraries = TestLibrariesRepository(threeLibraries()),
                    activeLibrary = TestActiveLibrary("a"),
                )

                switch.commit("b", SwitchKind.Switch)

                listOf("a", "b", "c").map { tokens.getTokenFlow(it).first() } shouldBe before
            }
        }

        it("#369 TC-9: removes no library") {
            runTest {
                val libraries = TestLibrariesRepository(threeLibraries())
                val switch = sut(libraries = libraries, activeLibrary = TestActiveLibrary("a"))

                switch.commit("b", SwitchKind.Switch)

                libraries.removedIds.shouldBeEmpty()
            }
        }
    }

    describe("waiting for playback to stop") {

        it("#369 TC-10: restarts once the stop wait has passed, not before") {
            runTest {
                val restarter = TestAppRestarter()
                val switch = sut(
                    libraries = TestLibrariesRepository(threeLibraries()),
                    activeLibrary = TestActiveLibrary("a"),
                    audiobook = TestAudiobookController(playbackState(randomPlayingBook())),
                    restarter = restarter,
                )
                val commit = async { switch.commit("b", SwitchKind.Switch) }
                runCurrent()

                advanceTimeBy(STOP_WAIT.inWholeMilliseconds - 1)
                runCurrent()
                restarter.restarts shouldBe 0

                advanceTimeBy(1)
                runCurrent()
                restarter.restarts shouldBe 1
                commit.await()
            }
        }

        it("#369 TC-10: restarts at once when playback unloads before the wait ends") {
            runTest {
                val audiobook = TestAudiobookController(playbackState(randomPlayingBook()))
                val restarter = TestAppRestarter()
                val switch = sut(
                    libraries = TestLibrariesRepository(threeLibraries()),
                    activeLibrary = TestActiveLibrary("a"),
                    audiobook = audiobook,
                    restarter = restarter,
                )
                val commit = async { switch.commit("b", SwitchKind.Switch) }
                runCurrent()

                advanceTimeBy(1.seconds.inWholeMilliseconds)
                audiobook.state.value = PlaybackState()
                runCurrent()

                restarter.restarts shouldBe 1
                commit.await()
            }
        }
    }
})
