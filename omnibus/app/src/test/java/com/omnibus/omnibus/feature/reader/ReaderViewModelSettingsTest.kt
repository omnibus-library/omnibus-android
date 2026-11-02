package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.progress.FakeProgressRepository
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.data.reader.local.FakeReaderSettingsStore
import com.omnibus.omnibus.data.reader.local.ReaderSettingsStore
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.di.TimeSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The reader's settings in the ViewModel: loaded before the page boots, changed and saved on every tap. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSettingsTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    lateinit var persistScope: CoroutineScope

    beforeEach { persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    afterEach { persistScope.cancel() }

    /** Builds a ViewModel for [bookUuid] over [store], with the other collaborators as in the other VM tests. */
    fun viewModel(
        store: ReaderSettingsStore,
        bookUuid: String = "book-1",
    ) = ReaderViewModel(
        bookUuid = bookUuid,
        libraryRepository = FakeLibraryRepository(),
        assetLoaderFactory = ReaderAssetLoaderFactory(
            mockk(relaxed = true),
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.NotFound },
        ),
        progressRepository = FakeProgressRepository(),
        settingsStore = store,
        timeSource = TimeSource { 0L },
        persistScope = persistScope,
    )

    describe("loading before the page boots") {

        it("keeps the gate closed until the stored settings are read, then holds them") {
            val stored = ReaderSettings(theme = ReaderTheme.Sepia, fontSize = 24)
            val store = FakeReaderSettingsStore(stored).apply { holdFirstEmission = CompletableDeferred() }

            val vm = viewModel(store)
            vm.uiState.value.settingsLoaded shouldBe false

            store.holdFirstEmission!!.complete(Unit)

            vm.uiState.value.settingsLoaded shouldBe true
            vm.uiState.value.settings shouldBe stored
        }

        it("opens the gate with the defaults when nothing is stored, and a hand-built state is loaded") {
            val vm = viewModel(FakeReaderSettingsStore())

            vm.uiState.value.settingsLoaded shouldBe true
            vm.uiState.value.settings shouldBe ReaderSettings()
            ReaderUIState().settingsLoaded shouldBe true
        }

        it("reads the store once and ignores a later emission") {
            val store = FakeReaderSettingsStore(ReaderSettings(fontSize = 22))
            val vm = viewModel(store)

            store.emit(ReaderSettings(fontSize = 30))

            vm.uiState.value.settings shouldBe ReaderSettings(fontSize = 22)
        }
    }

    describe("changing settings") {

        it("updates the state and saves the normalized value") {
            val store = FakeReaderSettingsStore()
            val vm = viewModel(store)

            vm.onSettingsChanged(ReaderSettings(fontSize = 99, lineHeight = 1.64))

            val normalized = ReaderSettings(fontSize = 34, lineHeight = 1.6)
            vm.uiState.value.settings shouldBe normalized
            store.saves shouldBe listOf(normalized)
            store.settings.first() shouldBe normalized
        }

        it("ignores a change equal to the current value, saving nothing") {
            val store = FakeReaderSettingsStore()
            val vm = viewModel(store)

            vm.onSettingsChanged(vm.uiState.value.settings)

            store.saves.isEmpty() shouldBe true
        }

        it("keeps the last of three rapid taps in state and in the store, in tap order") {
            val store = FakeReaderSettingsStore()
            val vm = viewModel(store)
            val taps = listOf(
                ReaderSettings(fontSize = 20),
                ReaderSettings(fontSize = 21, theme = ReaderTheme.Light),
                ReaderSettings(fontSize = 22, theme = ReaderTheme.Light, margins = ReaderMargins.Wide),
            )

            taps.forEach { vm.onSettingsChanged(it) }

            store.saves shouldBe taps
            vm.uiState.value.settings shouldBe taps.last()
            store.settings.first() shouldBe taps.last()
        }

        it("keeps the new value in state when the save fails") {
            val store = FakeReaderSettingsStore().apply { saveFails = true }
            val vm = viewModel(store)
            val changed = ReaderSettings(typeface = ReaderTypeface.Mono)

            vm.onSettingsChanged(changed)

            vm.uiState.value.settings shouldBe changed
            store.saves shouldBe listOf(changed)
        }

        it("loads the saved settings in a new view model for another book") {
            val store = FakeReaderSettingsStore()
            viewModel(store, bookUuid = "book-1").onSettingsChanged(
                ReaderSettings(theme = ReaderTheme.Black, typeface = ReaderTypeface.Editorial),
            )

            val second = viewModel(store, bookUuid = "book-2")

            second.uiState.value.settings shouldBe ReaderSettings(
                theme = ReaderTheme.Black,
                typeface = ReaderTypeface.Editorial,
            )
        }
    }
})
