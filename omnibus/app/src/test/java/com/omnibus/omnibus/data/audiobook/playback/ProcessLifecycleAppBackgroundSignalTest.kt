package com.omnibus.omnibus.data.audiobook.playback

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.cash.turbine.test
import io.kotest.core.spec.style.DescribeSpec

class ProcessLifecycleAppBackgroundSignalTest : DescribeSpec({

    describe("signalling that the app went to the background") {
        // TC-89
        it("emits each time the app stops (R10)") {
            lateinit var registry: LifecycleRegistry
            val owner = object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = registry
            }
            registry = LifecycleRegistry.createUnsafe(owner)

            ProcessLifecycleAppBackgroundSignal(registry).backgrounded.test {
                registry.currentState = Lifecycle.State.STARTED
                expectNoEvents()

                registry.currentState = Lifecycle.State.CREATED
                awaitItem()

                registry.currentState = Lifecycle.State.STARTED
                expectNoEvents()

                registry.currentState = Lifecycle.State.CREATED
                awaitItem()
            }
        }
    }
})
