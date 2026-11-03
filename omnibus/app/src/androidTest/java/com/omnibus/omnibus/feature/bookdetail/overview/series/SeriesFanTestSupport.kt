package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** Member [i] (1-based), with uuid "m$i" and the title "Title $i". */
internal fun member(i: Int, index: String? = "$i") = SeriesMember("m$i", "Title $i", index, null, null, false)

/** Members 1..[n] in list order; [indexes] gives each one's series index. */
internal fun members(n: Int, indexes: (Int) -> String? = { "$it" }) = (1..n).map { member(it, indexes(it)) }

/** A series book at [position] (0-based) of the members, or an absent book when [position] is null. */
internal fun seriesBook(position: Int?, series: String? = "Dune") = testDetailBook(
    uniqueIdentifier = position?.let { "m${it + 1}" } ?: "absent",
    series = series,
    seriesIndex = position?.let { "${it + 1}" },
).copy(seriesId = 4L)

/** The fan model for [n] members with this book at [current] (0-based, or absent when null). */
internal fun fanModel(n: Int, current: Int?, list: List<SeriesMember> = members(n)) =
    seriesFanModel(seriesBook(current), list)!!

/** [dp] in pixels for this rule's density. */
internal fun ComposeContentTestRule.px(dp: Dp): Float = with(density) { dp.toPx() }

/**
 * The node's bounds in root coordinates before any ancestor clip. A row that scrolls clips its cards' semantics
 * bounds, so a card past the viewport would report a clamped edge; its layout position is what the fan defines.
 */
internal fun SemanticsNodeInteraction.layoutBounds(): Rect = fetchSemanticsNode().let { node ->
    val origin = node.positionInRoot
    Rect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
}

/** The `OnClick` label of a node, as TalkBack reads it. */
internal fun SemanticsNodeInteraction.clickLabel(): String =
    fetchSemanticsNode().config[SemanticsActions.OnClick].label!!

/** The single content description of a node, or null when it has none. */
internal fun SemanticsNodeInteraction.description(): String? =
    fetchSemanticsNode().config.let { config ->
    if (config.contains(SemanticsProperties.ContentDescription)) config[SemanticsProperties.ContentDescription].single() else null
}

/** Test state for a stateless [SeriesFan]: the model, the spread flag, the row scroll and what the callbacks saw. */
internal class FanState(model: SeriesFanModel, spread: Boolean = false) {
    var model by mutableStateOf(model)
    var spread by mutableStateOf(spread)
    var scroll by mutableStateOf(ScrollState(0))
    var epoch by mutableStateOf(0)
    var toggleCount = 0
    val opened = mutableListOf<String>()
}

/** Hosts a [SeriesFan] over [state] in dark theme, padded like the Overview panel. */
internal fun ComposeContentTestRule.setFan(
    state: FanState,
    reducedMotion: Boolean = true,
    direction: LayoutDirection = LayoutDirection.Ltr,
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    key(state.epoch) {
                        SeriesFan(
                            model = state.model,
                            spread = state.spread,
                            onToggleSpread = { state.toggleCount++ },
                            onOpenBook = { state.opened += it },
                            onOpenSeriesPage = {},
                            ribbonColor = Color.Magenta,
                            reducedMotion = reducedMotion,
                            scrollState = state.scroll,
                        )
                    }
                }
            }
        }
    }
}
