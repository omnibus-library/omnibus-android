package com.omnibus.omnibus.feature.reader

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.local.TestReaderSettingsStore
import com.omnibus.omnibus.data.reader.randomReaderSettings
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The reader's settings in the ViewModel: loaded before the page boots, changed and saved on every tap. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelSettingsTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    /** A copy that differs in font size, whichever size this is. */
    fun ReaderSettings.withOtherFontSize() = withFontSizeStep(if (canIncreaseFontSize) 1 else -1)

    describe("loading") {
        context("before the page boots") {
            it("keeps the gate closed until the stored settings are read") {
                val stored = randomReaderSettings()
                val hold = CompletableDeferred<Unit>()
                val store = TestReaderSettingsStore(stored).apply { holdFirstEmission = hold }

                val vm = sut(settingsStore = store)
                vm.uiState.value.settingsLoaded shouldBe false

                hold.complete(Unit)

                vm.uiState.value.settingsLoaded shouldBe true
                vm.uiState.value.settings shouldBe stored
            }

            it("ignores a later emission from the store") {
                val stored = randomReaderSettings()
                val store = TestReaderSettingsStore(stored)
                val vm = sut(settingsStore = store)

                store.emit(stored.withOtherFontSize())

                vm.uiState.value.settings shouldBe stored
            }
        }
    }

    describe("changing settings") {
        it("updates the state with the normalized value") {
            val store = TestReaderSettingsStore()
            val vm = sut(settingsStore = store)
            val out = randomReaderSettings().copy(fontSize = 99, lineHeight = 1.64)
            val normalized = out.copy(fontSize = 34, lineHeight = 1.6)

            vm.onSettingsChanged(out)

            vm.uiState.value.settings shouldBe normalized
            store.saves shouldBe listOf(normalized)
        }

        context("with the current value") {
            it("saves nothing") {
                val store = TestReaderSettingsStore()
                val vm = sut(settingsStore = store)

                vm.onSettingsChanged(vm.uiState.value.settings)

                store.saves.shouldBeEmpty()
            }
        }

        context("with rapid taps") {
            it("saves them in tap order") {
                val store = TestReaderSettingsStore(ReaderSettings(fontSize = 19))
                val vm = sut(settingsStore = store)
                val taps = listOf(20, 21, 22).map { ReaderSettings(fontSize = it) }

                taps.forEach(vm::onSettingsChanged)

                store.saves shouldBe taps
            }
        }

        context("when the view model is cleared during the save") {
            it("completes the save") {
                val settings = randomReaderSettings()
                val hold = CompletableDeferred<Unit>()
                val store = TestReaderSettingsStore().apply { holdSaves = hold }
                val owner = ViewModelStore()
                val vm = ViewModelProvider.create(
                    owner,
                    viewModelFactory { initializer { sut(settingsStore = store) } },
                )[ReaderViewModel::class]
                vm.onSettingsChanged(settings)
                owner.clear()

                hold.complete(Unit)

                store.settings.first() shouldBe settings.normalized()
            }
        }
    }
})
