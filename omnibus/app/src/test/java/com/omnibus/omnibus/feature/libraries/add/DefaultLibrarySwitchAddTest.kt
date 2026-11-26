package com.omnibus.omnibus.feature.libraries.add

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.libraries.ActiveLibrary
import com.omnibus.omnibus.data.libraries.DefaultLibrarySwitch
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.PendingOpening
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.data.libraries.TestActiveLibrary
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.local.PendingOpeningStore
import com.omnibus.omnibus.data.libraries.local.TestPendingOpeningStore
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.di.AppRestarter
import com.omnibus.omnibus.di.TestAppRestarter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class DefaultLibrarySwitchAddTest : DescribeSpec({

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

    describe("committing a switch to a library that was just added") {

        it("#371 TC-13: stops playback, saves the pending opening, makes the target active, then restarts, in that order") {
            runTest {
                val log = mutableListOf<String>()
                val active = randomLibrary().copy(isActive = true)
                val added = randomLibrary().copy(isActive = false)
                val libraries = TestLibrariesRepository(listOf(active, added))
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
                            return active.id
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

                switch.commit(added.id, SwitchKind.Add)

                log shouldBe listOf("activeId", "stop", "save", "setActive", "restart")
            }
        }

        it("#371 TC-13: saves the pending opening for the target as an add") {
            runTest {
                val pending = TestPendingOpeningStore()
                val active = randomLibrary().copy(isActive = true)
                val added = randomLibrary().copy(isActive = false)
                val switch = sut(
                    libraries = TestLibrariesRepository(listOf(active, added)),
                    activeLibrary = TestActiveLibrary(active.id),
                    pendingOpening = pending,
                )

                switch.commit(added.id, SwitchKind.Add)

                pending.saves shouldBe listOf(PendingOpening(added.id, SwitchKind.Add))
            }
        }
    }
})
