package com.omnibus.omnibus.testing

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

/** Stubs `android.util.Log` so code under test can log on the JVM. Pair with [unmockAndroidLog]. */
fun mockAndroidLog() {
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.d(any(), any(), any()) } returns 0
    every { Log.i(any(), any()) } returns 0
    every { Log.i(any(), any(), any()) } returns 0
    every { Log.w(any(), any<String>()) } returns 0
    every { Log.w(any(), any<Throwable>()) } returns 0
    every { Log.w(any(), any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0
}

/** Undoes [mockAndroidLog]. */
fun unmockAndroidLog() {
    unmockkStatic(Log::class)
}
