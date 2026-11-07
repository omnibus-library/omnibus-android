package com.omnibus.omnibus.feature.bookdetail.overview.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot
import java.util.GregorianCalendar
import java.util.TimeZone

// Dates are built in the default zone, which the card formats them in, so the shot is the same in every zone.
private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    GregorianCalendar(TimeZone.getDefault()).apply { clear(); set(2026, month - 1, day, hour, minute) }.timeInMillis

private val now = at(11, 2)
private val oct12 = at(10, 12, 20)
private val oct19 = at(10, 19, 18)
private val nov1At2214 = at(11, 1, 22, 14)

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingCardReading() {
    ReadingCardShot(
        ReadingCardModel(
            line = ReadingLine.ReadingSince(oct12),
            rows = listOf(
                ReadingRow.Started(oct12),
                ReadingRow.TimeRead(11_040L, 11),
                ReadingRow.Pace(20),
                ReadingRow.LastRead(nov1At2214),
                ReadingRow.LongestSitting(2_880L, oct19),
                ReadingRow.TimeLeft(6_780L),
            ),
        ),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingCardNoReading() {
    ReadingCardShot(ReadingCardModel(ReadingLine.NoReading, emptyList()))
}

@PreviewTest
@PreviewLightDark
@Composable
fun ReadingCardHold() {
    ReadingCardShot(ReadingCardModel(line = null, rows = emptyList()))
}

@Composable
private fun ReadingCardShot(card: ReadingCardModel) {
    Shot {
        Column(Modifier.fillMaxWidth()) { ReadingCard(card, now) }
    }
}
