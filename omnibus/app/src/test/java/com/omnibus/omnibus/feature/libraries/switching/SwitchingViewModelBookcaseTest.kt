package com.omnibus.omnibus.feature.libraries.switching

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibrarySwitch
import com.omnibus.omnibus.data.libraries.LibrarySync
import com.omnibus.omnibus.data.libraries.LibrarySyncResult
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrarySwitch
import com.omnibus.omnibus.data.libraries.TestLibrarySync
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.monogramOf
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

/** The bookcase the switching view model builds for each stage, and the lift's timing (#369). */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchingViewModelBookcaseTest : DescribeSpec({

    // The lift waits on the main dispatcher's virtual time, so the tests drive it through this scheduler.
    val mainDispatcher = UnconfinedTestDispatcher()

    beforeSpec { Dispatchers.setMain(mainDispatcher) }

    afterSpec { Dispatchers.resetMain() }

    fun sut(
        libraries: LibrariesRepository,
        route: SwitchingRoute = SwitchingRoute("work"),
        sync: LibrarySync = TestLibrarySync(),
        librarySwitch: LibrarySwitch = TestLibrarySwitch(),
    ): SwitchingViewModel = SwitchingViewModel(
        route = route,
        libraries = libraries,
        sync = sync,
        librarySwitch = librarySwitch,
        auth = TestAuthRepository(),
        persistScope = CoroutineScope(UnconfinedTestDispatcher()),
    )

    /** Three libraries in addedAt order. "home" is active, so it is the current library for a switch to "work". */
    fun shelf(): List<Library> = listOf(
        randomLibrary().copy(id = "home", name = "Home", addedAt = 1, isActive = true),
        randomLibrary().copy(id = "reading-group", name = "Reading group", addedAt = 2, isActive = false),
        randomLibrary().copy(id = "work", name = "Work", addedAt = 3, isActive = false),
    )

    fun spines(libraries: List<Library>) = libraries.map {
        BookcaseSpine(it.id, monogramOf(it.displayName()), SpineHeights.of(it.id))
    }

    /**
     * Asserts the lift holds for LIFT: one millisecond before it has passed, the view model is still lifting and nothing
     * is committed. Once it has passed, the bookcase rests with the target raised, and the switch is committed.
     */
    fun assertTheLiftHoldsForLift(viewModel: SwitchingViewModel, switch: TestLibrarySwitch) {
        val scheduler = mainDispatcher.scheduler

        scheduler.advanceTimeBy(BookcaseGeometry.LIFT.inWholeMilliseconds - 1)
        viewModel.uiState.value.stage shouldBe SwitchingStage.Lifting
        switch.commits.shouldBeEmpty()

        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
        viewModel.uiState.value.stage shouldBe SwitchingStage.Opening
        viewModel.uiState.value.bookcase.transition shouldBe BookcaseTransition.Still
        viewModel.uiState.value.bookcase.raisedLibraryId shouldBe "work"
        switch.commits shouldBe listOf("work" to SwitchKind.Switch)
    }

    describe("the bookcase while syncing the current library") {

        it("#369 TC-30: lists every library as a spine and raises the current one") {
            val libraries = shelf()
            val viewModel = sut(
                libraries = TestLibrariesRepository(libraries),
                sync = TestLibrarySync().apply {
                    hasUnsynced = true
                    gate = CompletableDeferred()
                },
            )

            viewModel.uiState.value.stage shouldBe SwitchingStage.Syncing
            viewModel.uiState.value.bookcase shouldBe BookcaseState(spines(libraries), "home", BookcaseTransition.Still)
        }
    }

    describe("the bookcase after a failed sync") {

        it("#369 TC-30: keeps the current one raised") {
            val libraries = shelf()
            val viewModel = sut(
                libraries = TestLibrariesRepository(libraries),
                sync = TestLibrarySync().apply {
                    hasUnsynced = true
                    result = LibrarySyncResult.Failed
                },
            )

            viewModel.uiState.value.stage shouldBe SwitchingStage.SyncFailed
            viewModel.uiState.value.bookcase shouldBe BookcaseState(spines(libraries), "home", BookcaseTransition.Still)
        }
    }

    describe("the bookcase while lifting after a successful sync") {

        it("#369 TC-30: lifts from the current library to the target") {
            val libraries = shelf()
            val gate = CompletableDeferred<LibrarySyncResult>()
            val viewModel = sut(
                libraries = TestLibrariesRepository(libraries),
                sync = TestLibrarySync().apply {
                    hasUnsynced = true
                    this.gate = gate
                },
            )

            gate.complete(LibrarySyncResult.Synced)

            viewModel.uiState.value.stage shouldBe SwitchingStage.Lifting
            viewModel.uiState.value.bookcase shouldBe
                BookcaseState(spines(libraries), "work", BookcaseTransition.Lift(fromId = "home", toId = "work"))
        }

        it("#369 TC-30: commits only once LIFT has passed") {
            val switch = TestLibrarySwitch()
            val gate = CompletableDeferred<LibrarySyncResult>()
            val viewModel = sut(
                libraries = TestLibrariesRepository(shelf()),
                sync = TestLibrarySync().apply {
                    hasUnsynced = true
                    this.gate = gate
                },
                librarySwitch = switch,
            )

            gate.complete(LibrarySyncResult.Synced)

            assertTheLiftHoldsForLift(viewModel, switch)
        }
    }

    describe("the bookcase while lifting with nothing unsynced") {

        it("#369 TC-30: lifts from the current library to the target") {
            val libraries = shelf()
            val viewModel = sut(
                libraries = TestLibrariesRepository(libraries),
                sync = TestLibrarySync().apply { hasUnsynced = false },
            )

            viewModel.uiState.value.stage shouldBe SwitchingStage.Lifting
            viewModel.uiState.value.bookcase shouldBe
                BookcaseState(spines(libraries), "work", BookcaseTransition.Lift(fromId = "home", toId = "work"))
        }

        it("#369 TC-30: commits only once LIFT has passed") {
            val switch = TestLibrarySwitch()
            val viewModel = sut(
                libraries = TestLibrariesRepository(shelf()),
                sync = TestLibrarySync().apply { hasUnsynced = false },
                librarySwitch = switch,
            )

            assertTheLiftHoldsForLift(viewModel, switch)
        }
    }

    describe("the bookcase after switching anyway") {

        it("#369 TC-30: lifts from the current library to the target") {
            val libraries = shelf()
            val viewModel = sut(
                libraries = TestLibrariesRepository(libraries),
                sync = TestLibrarySync().apply {
                    hasUnsynced = true
                    result = LibrarySyncResult.Failed
                },
            )

            viewModel.onSwitchAnyway()

            viewModel.uiState.value.stage shouldBe SwitchingStage.Lifting
            viewModel.uiState.value.bookcase shouldBe
                BookcaseState(spines(libraries), "work", BookcaseTransition.Lift(fromId = "home", toId = "work"))
        }

        it("#369 TC-30: commits only once LIFT has passed") {
            val switch = TestLibrarySwitch()
            val viewModel = sut(
                libraries = TestLibrariesRepository(shelf()),
                sync = TestLibrarySync().apply {
                    hasUnsynced = true
                    result = LibrarySyncResult.Failed
                },
                librarySwitch = switch,
            )

            viewModel.onSwitchAnyway()

            assertTheLiftHoldsForLift(viewModel, switch)
        }
    }

    describe("the bookcase on the Opening stage after a restart") {

        it("#369 TC-30: raises the target at rest") {
            val libraries = shelf()
            val viewModel = sut(
                libraries = TestLibrariesRepository(libraries),
                route = SwitchingRoute("work", atOpening = true),
            )

            viewModel.uiState.value.stage shouldBe SwitchingStage.Opening
            viewModel.uiState.value.bookcase shouldBe BookcaseState(spines(libraries), "work", BookcaseTransition.Still)
        }
    }
})
