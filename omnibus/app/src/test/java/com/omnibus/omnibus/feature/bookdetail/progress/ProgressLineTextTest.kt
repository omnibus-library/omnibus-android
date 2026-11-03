package com.omnibus.omnibus.feature.bookdetail.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.util.Locale

/** The English templates, as the string resources hold them. */
private val English = ProgressStrings(
    chapterFlag = "Ch. %1\$d",
    percent = "%1\$d%%",
    leftAtPace = " · about %1\$s left at your pace",
    leftOf = " · %1\$s left of %2\$s",
    underAMinute = "0 m",
)

class ProgressLineTextTest : DescribeSpec({

    describe("the flag and the line") {

        it("TC-47: line and flag wording") {
            FlagLabel.Chapter(6).text(English, Locale.US) shouldBe "Ch. 6"
            FlagLabel.Percent(62).text(English, Locale.US) shouldBe "62%"
            ProgressDisplay.Bar(0.62f, 62, false, emptyList(), FlagLabel.Percent(62), null)
                .percentText(English, Locale.US) shouldBe "62%"
            FlagLabel.Elapsed(9060).text(English, Locale.US) shouldBe "2 h 31 m"
            FlagLabel.Elapsed(30).text(English, Locale.US) shouldBe "0 m"

            val atPace = LineTail.LeftAtPace(6780)
            atPace.text(English, Locale.US) shouldBe " · about 1 h 53 m left at your pace"
            atPace.text(English, Locale.US) shouldStartWith " · "

            val of = LineTail.LeftOf(6540, 17340)
            of.text(English, Locale.US) shouldBe " · 1 h 49 m left of 4 h 49 m"
            of.text(English, Locale.US) shouldStartWith " · "
        }
    }
})
