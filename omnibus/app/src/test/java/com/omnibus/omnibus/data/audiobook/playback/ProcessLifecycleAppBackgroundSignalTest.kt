package com.omnibus.omnibus.data.audiobook.playback

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class ProcessLifecycleAppBackgroundSignalTest : DescribeSpec({

    describe("backgrounded") {

        // TC-89
        it("emits on ON_STOP (R10)") {
            runTest(UnconfinedTestDispatcher()) {
                lateinit var registry: LifecycleRegistry
                val owner = object : LifecycleOwner {
                    override val lifecycle: Lifecycle get() = registry
                }
                registry = LifecycleRegistry.createUnsafe(owner)
                val signal = ProcessLifecycleAppBackgroundSignal(registry)
                val emissions = mutableListOf<Unit>()
                val job = launch { signal.backgrounded.collect { emissions += Unit } }

                registry.currentState = Lifecycle.State.CREATED
                registry.currentState = Lifecycle.State.STARTED
                emissions.size shouldBe 0

                registry.currentState = Lifecycle.State.CREATED
                emissions.size shouldBe 1

                registry.currentState = Lifecycle.State.STARTED
                registry.currentState = Lifecycle.State.CREATED
                emissions.size shouldBe 2

                job.cancel()
            }
        }

        // TC-89
        it("removes its observer when collection stops (R10)") {
            runTest(UnconfinedTestDispatcher()) {
                lateinit var registry: LifecycleRegistry
                val owner = object : LifecycleOwner {
                    override val lifecycle: Lifecycle get() = registry
                }
                registry = LifecycleRegistry.createUnsafe(owner)
                val signal = ProcessLifecycleAppBackgroundSignal(registry)
                val job = launch { signal.backgrounded.collect {} }

                registry.observerCount shouldBe 1

                job.cancel()
                job.join()
                registry.observerCount shouldBe 0
            }
        }
    }
})
