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
            // The registry holds its owner weakly, so the test keeps the owner and reaches the registry through it.
            val owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this)
            }

            ProcessLifecycleAppBackgroundSignal(owner.lifecycle).backgrounded.test {
                owner.lifecycle.currentState = Lifecycle.State.STARTED
                expectNoEvents()

                owner.lifecycle.currentState = Lifecycle.State.CREATED
                awaitItem()

                owner.lifecycle.currentState = Lifecycle.State.STARTED
                expectNoEvents()

                owner.lifecycle.currentState = Lifecycle.State.CREATED
                awaitItem()
            }
        }
    }
})
