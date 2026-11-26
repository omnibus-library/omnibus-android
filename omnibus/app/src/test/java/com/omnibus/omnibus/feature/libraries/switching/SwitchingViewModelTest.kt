package com.omnibus.omnibus.feature.libraries.switching

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.LibrarySwitch
import com.omnibus.omnibus.data.libraries.LibrarySync
import com.omnibus.omnibus.data.libraries.LibrarySyncResult
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrarySwitch
import com.omnibus.omnibus.data.libraries.TestLibrarySync
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    afterSpec { Dispatchers.resetMain() }

    fun sut(
        route: SwitchingRoute = SwitchingRoute(toLibraryId = randomString()),
        libraries: LibrariesRepository = TestLibrariesRepository(),
        sync: LibrarySync = TestLibrarySync(),
        librarySwitch: LibrarySwitch = TestLibrarySwitch(),
        auth: TestAuthRepository = TestAuthRepository(),
        persistScope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ): SwitchingViewModel = SwitchingViewModel(route, libraries, sync, librarySwitch, auth, persistScope)

    /** A current library, active, and a target that is not. */
    fun home() = randomLibrary().copy(id = "home", name = "Home", isActive = true)

    fun work() = randomLibrary().copy(id = "work", name = "Work", isActive = false)

    describe("a switch with nothing unsynced") {

        it("#369 TC-20: does not sync the current library") {
            val sync = TestLibrarySync().apply { hasUnsynced = false }

            sut(
                route = SwitchingRoute("work"),
                libraries = TestLibrariesRepository(listOf(home(), work())),
                sync = sync,
            )

            sync.syncCalls shouldBe 0
        }

        it("#369 TC-20: commits the switch once") {
            val switch = TestLibrarySwitch()

            sut(
                route = SwitchingRoute("work"),
                libraries = TestLibrariesRepository(listOf(home(), work())),
                sync = TestLibrarySync().apply { hasUnsynced = false },
                librarySwitch = switch,
            )

            switch.commits shouldBe listOf("work" to SwitchKind.Switch)
        }
    }

    describe("a switch with unsynced changes") {

        it("#369 TC-20: shows the syncing stage with the current library while the sync runs") {
            val gate = CompletableDeferred<LibrarySyncResult>()
            val sync = TestLibrarySync().apply {
                hasUnsynced = true
                this.gate = gate
            }

            val viewModel = sut(
                route = SwitchingRoute("work"),
                libraries = TestLibrariesRepository(listOf(home(), work())),
                sync = sync,
            )

            viewModel.uiState.value.stage shouldBe SwitchingStage.Syncing
            viewModel.uiState.value.current?.id shouldBe "home"
        }

        it("#369 TC-20: commits the switch once the sync succeeds") {
            val gate = CompletableDeferred<LibrarySyncResult>()
            val switch = TestLibrarySwitch()
            val sync = TestLibrarySync().apply {
                hasUnsynced = true
                this.gate = gate
            }
            sut(
                route = SwitchingRoute("work"),
                libraries = TestLibrariesRepository(listOf(home(), work())),
                sync = sync,
                librarySwitch = switch,
            )

            gate.complete(LibrarySyncResult.Synced)

            switch.commits shouldBe listOf("work" to SwitchKind.Switch)
        }
    }

    describe("a failed sync") {

        fun failedViewModel(
            switch: TestLibrarySwitch,
            sync: TestLibrarySync,
        ): SwitchingViewModel {
            sync.hasUnsynced = true
            sync.result = LibrarySyncResult.Failed
            return sut(
                route = SwitchingRoute("work"),
                libraries = TestLibrariesRepository(listOf(home(), work())),
                sync = sync,
                librarySwitch = switch,
            )
        }

        it("#369 TC-21: shows the sync failure, still on the current library, without committing") {
            val switch = TestLibrarySwitch()

            val viewModel = failedViewModel(switch, TestLibrarySync())

            viewModel.uiState.value.stage shouldBe SwitchingStage.SyncFailed
            viewModel.uiState.value.current?.id shouldBe "home"
            switch.commits.shouldBeEmpty()
        }

        it("#369 TC-21: staying leaves the switch and commits nothing") {
            val switch = TestLibrarySwitch()
            val viewModel = failedViewModel(switch, TestLibrarySync())

            viewModel.onStay()

            viewModel.uiState.value.left shouldBe true
            switch.commits.shouldBeEmpty()
        }

        it("#369 TC-21: switching anyway commits once, without syncing again") {
            val switch = TestLibrarySwitch()
            val sync = TestLibrarySync()
            val viewModel = failedViewModel(switch, sync)

            viewModel.onSwitchAnyway()

            switch.commits shouldBe listOf("work" to SwitchKind.Switch)
            sync.syncCalls shouldBe 1
        }
    }

    describe("the Opening stage after a restart") {

        it("#369 TC-22: stays in Opening until the target library is read") {
            val libraries = TestLibrariesRepository()
            val viewModel = sut(route = SwitchingRoute("work", atOpening = true), libraries = libraries)

            viewModel.uiState.value.stage shouldBe SwitchingStage.Opening
            viewModel.uiState.value.opened shouldBe false

            libraries.libraries.value = listOf(home(), work())

            viewModel.uiState.value.opened shouldBe true
        }

        it("#369 TC-22: does not open until the session state has been read") {
            val auth = TestAuthRepository().apply { isSignedInGate = CompletableDeferred() }
            val libraries = TestLibrariesRepository(listOf(home(), work()))
            val viewModel = sut(route = SwitchingRoute("work", atOpening = true), libraries = libraries, auth = auth)

            viewModel.uiState.value.opened shouldBe false

            auth.isSignedInGate!!.complete(Unit)

            viewModel.uiState.value.opened shouldBe true
        }

        it("#369 TC-22: clears the pending opening before it opens") {
            val switch = TestLibrarySwitch()
            val viewModel = sut(
                route = SwitchingRoute("work", atOpening = true),
                libraries = TestLibrariesRepository(listOf(home(), work())),
                librarySwitch = switch,
            )

            switch.clears shouldBe 1
            viewModel.uiState.value.opened shouldBe true
        }

        it("#369 TC-22: never commits or syncs") {
            val switch = TestLibrarySwitch()
            val sync = TestLibrarySync()

            sut(
                route = SwitchingRoute("work", atOpening = true),
                libraries = TestLibrariesRepository(listOf(home(), work())),
                sync = sync,
                librarySwitch = switch,
            )

            switch.commits.shouldBeEmpty()
            sync.syncCalls shouldBe 0
        }
    }

    describe("the view model being cleared during a switch") {

        it("#369 TC-23: still completes the commit") {
            val gate = CompletableDeferred<Unit>()
            val switch = TestLibrarySwitch().apply { commitGate = gate }
            val store = ViewModelStore()
            val libraries = TestLibrariesRepository(listOf(home(), work()))
            ViewModelProvider.create(
                store,
                viewModelFactory {
                    initializer {
                        SwitchingViewModel(
                            SwitchingRoute("work"),
                            libraries,
                            TestLibrarySync(),
                            switch,
                            TestAuthRepository(),
                            CoroutineScope(UnconfinedTestDispatcher()),
                        )
                    }
                },
            )[SwitchingViewModel::class]
            store.clear()

            gate.complete(Unit)

            switch.commits shouldBe listOf("work" to SwitchKind.Switch)
        }
    }
})
