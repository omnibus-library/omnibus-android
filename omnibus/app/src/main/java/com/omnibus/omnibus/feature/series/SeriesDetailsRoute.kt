package com.omnibus.omnibus.feature.series

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One series' Series Details page, pushed on the stack of the tab that opened it.
 *
 * [instance] is new for each push and outside equality, so a repeat of the same series gets its own state (spec R3)
 * while an equal route already on top is still not pushed twice.
 *
 * @property seriesId The series' id, as the series links carry it.
 * @property name The series' name as the link showed it, held in the top bar until the page loads.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeriesDetailsRoute(val seriesId: Long, val name: String) : NavKey {

    /** Kept through process death, so a restored page keeps its own state. */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val instance: String = UUID.randomUUID().toString()

    /** The nav entry's content key: one per push, stable across process death. */
    val contentKey: String get() = "series-details-$seriesId-$instance"
}
