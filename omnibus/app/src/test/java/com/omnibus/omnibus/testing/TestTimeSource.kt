package com.omnibus.omnibus.testing

import com.omnibus.omnibus.di.TimeSource

/** A [TimeSource] that tests set and advance by hand. Starts at [currentMillis]. */
class TestTimeSource(var currentMillis: Long = 0L) : TimeSource {

    override fun nowMillis(): Long = currentMillis

    /** Moves the clock forward by [millis]. */
    fun advanceBy(millis: Long) {
        currentMillis += millis
    }

    /** Sets the clock to [millis]. */
    fun set(millis: Long) {
        currentMillis = millis
    }
}
