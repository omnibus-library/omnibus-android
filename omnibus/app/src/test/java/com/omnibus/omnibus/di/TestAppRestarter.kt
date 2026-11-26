package com.omnibus.omnibus.di

/** An [AppRestarter] that counts restarts instead of ending the process. [onRestart] runs on each. */
class TestAppRestarter : AppRestarter {

    /** How many times [restart] was called. */
    var restarts = 0

    /** Runs on every [restart], after it is counted. */
    var onRestart: () -> Unit = {}

    override fun restart() {
        restarts++
        onRestart()
    }
}
