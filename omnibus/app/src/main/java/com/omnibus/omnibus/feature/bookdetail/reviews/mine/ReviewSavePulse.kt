package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts the saved ratings for each book, so the stage's stars can pulse after one. The count only grows, and a change
 * of the count is the signal. Bumping it is the only write, and a rating save and the sheet's save both call [bump].
 */
@Singleton
class ReviewSavePulse @Inject constructor() {

    private val counters = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    /** The save count for [bookUuid], which starts at 0. The same instance is returned for the same uuid. */
    fun pulses(bookUuid: String): StateFlow<Int> = counter(bookUuid)

    /** Counts one saved rating for [bookUuid]. */
    fun bump(bookUuid: String) {
        counter(bookUuid).update { it + 1 }
    }

    private fun counter(bookUuid: String): MutableStateFlow<Int> =
        counters.computeIfAbsent(bookUuid) { MutableStateFlow(0) }
}

/** Lets [rememberReviewSavePulse], a plain composable, reach the [ReviewSavePulse] singleton. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReviewSavePulseEntryPoint {
    /** The app's [ReviewSavePulse] singleton. */
    fun reviewSavePulse(): ReviewSavePulse
}

/**
 * The save count for [bookUuid], which changes once for each saved rating. It is 0 in previews and in hosts that are not
 * the app, where nothing pulses.
 */
@Composable
internal fun rememberReviewSavePulse(bookUuid: String): Int {
    if (LocalInspectionMode.current) return 0
    val context = LocalContext.current
    val pulse = remember(context) { reviewSavePulseOrNull(context) } ?: return 0
    return remember(pulse, bookUuid) { pulse.pulses(bookUuid) }.collectAsState().value
}

private fun reviewSavePulseOrNull(context: Context): ReviewSavePulse? = try {
    EntryPointAccessors.fromApplication(context.applicationContext, ReviewSavePulseEntryPoint::class.java)
        .reviewSavePulse()
} catch (_: IllegalStateException) {
    null
} catch (_: ClassCastException) {
    null
}
