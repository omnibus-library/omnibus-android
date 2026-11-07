package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The totals of the ticket's sample: 17 days, 13 sessions, 4 highlights. */
private val sampleTotals = listOf(FinishTotal.Days(17), FinishTotal.Sessions(13), FinishTotal.Highlights(4))

@PreviewTest
@PreviewLightDark
@Composable
fun FinishCardLeaveAReview() {
    FinishCardShot(FinishCardModel("The Hound", sampleTotals, FinishAction.Review))
}

/** A book with no finish date has no totals. */
@PreviewTest
@PreviewLightDark
@Composable
fun FinishCardNoTotals() {
    FinishCardShot(FinishCardModel("The Hound", emptyList(), FinishAction.Review))
}

@PreviewTest
@PreviewLightDark
@Composable
fun FinishCardUpNext() {
    val next = SeriesMember("uuid-3", "The Sign of the Four", "3", null, null, false)
    FinishCardShot(FinishCardModel("The Hound", sampleTotals, FinishAction.UpNext(next, "Sherlock Holmes")))
}

/** An unknown review has no action row. */
@PreviewTest
@PreviewLightDark
@Composable
fun FinishCardNoAction() {
    FinishCardShot(FinishCardModel("The Hound", sampleTotals, null))
}

@Composable
private fun FinishCardShot(model: FinishCardModel) {
    Shot {
        val ground = finishGround(Oklch(0.62, 0.12, 60.0), isLightGround(OmnibusTheme.palette.bg0))
        FinishCard(model, ground, onLeaveReview = {}, onOpenBook = {})
    }
}
