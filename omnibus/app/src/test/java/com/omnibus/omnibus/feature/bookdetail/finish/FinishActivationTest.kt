package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.feature.bookdetail.progress.ReadingProgressSection
import com.omnibus.omnibus.feature.bookdetail.slots.stageCoverOverlay
import com.omnibus.omnibus.feature.bookdetail.slots.stageProgress
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class FinishActivationTest : DescribeSpec({

    describe("finish card slots") {

        it("TC-27: the stage registers the card and the celebration on the #97 lines") {
            stageProgress shouldBe listOf(ReadingProgressSection, FinishCardSection)
            stageCoverOverlay shouldBe listOf(FinishCelebrationSection)

            val lines = File(
                System.getProperty("user.dir"),
                "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots/StageSlots.kt",
            ).readLines()
            val labels = lines.indices.filter { lines[it].contains("// #97 Finish Card and Celebration") }
            labels.size shouldBe 2
            lines[labels[0] + 1].trim() shouldBe "com.omnibus.omnibus.feature.bookdetail.finish.FinishCardSection,"
            lines[labels[1] + 1].trim() shouldBe "com.omnibus.omnibus.feature.bookdetail.finish.FinishCelebrationSection,"
        }
    }
})
