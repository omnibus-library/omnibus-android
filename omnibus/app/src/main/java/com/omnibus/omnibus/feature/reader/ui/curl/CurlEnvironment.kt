package com.omnibus.omnibus.feature.reader.ui.curl

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.view.Choreographer
import androidx.compose.runtime.staticCompositionLocalOf
import com.omnibus.omnibus.ui.theme.isReducedMotion
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** What the host asks the system at each turn. Tests replace it through [LocalCurlEnvironment]. */
internal class CurlEnvironment(val reducedMotion: () -> Boolean, val isLowRam: () -> Boolean) {
    companion object {
        /** The device's own answers: the animator scale and the low-RAM flag, read on every call. */
        fun system(context: Context): CurlEnvironment {
            val resolver = context.contentResolver
            val activityManager = context.getSystemService(ActivityManager::class.java)
            return CurlEnvironment(
                reducedMotion = {
                    isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
                },
                isLowRam = { activityManager?.isLowRamDevice == true },
            )
        }
    }
}

/** Overrides the [CurlEnvironment] for the reader below it; null means the device's own. */
internal val LocalCurlEnvironment = staticCompositionLocalOf<CurlEnvironment?> { null }

/** Suspends to the next frame and returns that frame's time in nanoseconds. */
internal interface FrameClock {
    suspend fun awaitFrame(): Long
}

/** The display's frame clock. Not `ValueAnimator`, so an animator scale of 0 does not collapse a curl. */
internal object ChoreographerFrameClock : FrameClock {
    override suspend fun awaitFrame(): Long = suspendCancellableCoroutine { continuation ->
        val choreographer = Choreographer.getInstance()
        val callback = Choreographer.FrameCallback { frameTimeNanos ->
            if (continuation.isActive) continuation.resume(frameTimeNanos)
        }
        choreographer.postFrameCallback(callback)
        continuation.invokeOnCancellation { choreographer.removeFrameCallback(callback) }
    }
}

/** One frame of a curl: the fold over the [front] page that lifts, and the [beneath] page it reveals. */
internal class CurlPicture(val scene: CurlScene, val front: PageImage, val beneath: PageImage)

/** Where the host shows its cover and its curl: the frame around the WebView. */
internal interface CurlStage {
    val widthPx: Int
    val heightPx: Int
    val density: Float

    /** Shows [image] over the page, or hides the cover when it is null. */
    fun showCover(image: PageImage?)

    /** Draws [picture] over the cover, or hides the curl when it is null. */
    fun showCurl(picture: CurlPicture?)
}
