package com.omnibus.omnibus.feature.bookdetail.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class ProgressLineTextTest : DescribeSpec({

    describe("the line's tail") {
        it("TC-47: reads the time left before the total") {
            val strings = ProgressStrings(
                chapterFlag = "Ch. %1\$d",
                percent = "%1\$d%%",
                leftAtPace = " · about %1\$s left at your pace",
                leftOf = " · %1\$s left of %2\$s",
                underAMinute = "0 m",
            )

            LineTail.LeftOf(6540, 17340).text(strings, Locale.US) shouldBe " · 1 h 49 m left of 4 h 49 m"
        }
    }
})
