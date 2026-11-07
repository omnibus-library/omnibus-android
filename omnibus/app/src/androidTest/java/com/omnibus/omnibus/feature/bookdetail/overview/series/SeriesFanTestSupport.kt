package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** Member [i] (1-based), with uuid "m$i" and the title "Title $i". */
internal fun member(i: Int) = SeriesMember("m$i", "Title $i", "$i", null, null, false)

/** Members 1..[n] in list order. */
internal fun members(n: Int) = (1..n).map { member(it) }

/** A series book at [position] (0-based) of the members, or an absent book when [position] is null. */
internal fun seriesBook(position: Int?, series: String? = "Dune") = testDetailBook(
    uniqueIdentifier = position?.let { "m${it + 1}" } ?: "absent",
    series = series,
    seriesIndex = position?.let { "${it + 1}" },
).copy(seriesId = 4L)

/** The fan model for [n] members with this book at [current] (0-based, or absent when null). */
internal fun fanModel(n: Int, current: Int?) = seriesFanModel(seriesBook(current), members(n))!!

/** The `OnClick` label of a node, as TalkBack reads it. */
internal fun SemanticsNodeInteraction.clickLabel(): String =
    fetchSemanticsNode().config[SemanticsActions.OnClick].label!!

/** Test state for a stateless [SeriesFan]: the model, the spread flag, the row scroll and what the callbacks saw. */
internal class FanState(model: SeriesFanModel, spread: Boolean = false) {
    var model by mutableStateOf(model)
    var spread by mutableStateOf(spread)
    var scroll by mutableStateOf(ScrollState(0))
    var toggleCount = 0
    val opened = mutableListOf<String>()
}

/** Hosts a [SeriesFan] over [state] in dark theme, padded like the Overview panel. */
internal fun ComposeContentTestRule.setFan(state: FanState) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                SeriesFan(
                    model = state.model,
                    spread = state.spread,
                    onToggleSpread = { state.toggleCount++ },
                    onOpenBook = { state.opened += it },
                    onOpenSeriesPage = {},
                    ribbonColor = Color.Magenta,
                    reducedMotion = true,
                    scrollState = state.scroll,
                )
            }
        }
    }
}
