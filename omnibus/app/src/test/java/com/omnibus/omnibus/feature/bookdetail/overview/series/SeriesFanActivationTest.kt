package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionSection
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.overviewSections
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** The Series section is activated on the Overview tab, after the description (issue #104). */
class SeriesFanActivationTest : DescribeSpec({

    describe("series fan slot") {

        it("#104 TC-65: the series section is activated after the description") {
            val registered = BookDetailSlots.Registered.overviewSections
            registered.contains(SeriesFanSection) shouldBe true
            overviewSections.contains(SeriesFanSection) shouldBe true
            (registered.indexOf(SeriesFanSection) > registered.indexOf(BookDescriptionSection)) shouldBe true
        }

        it("#104 TC-66: only the 104 line changed in OverviewSections.kt") {
            val source = File(
                System.getProperty("user.dir"),
                "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots/OverviewSections.kt",
            ).readLines().map { it.trim() }
            val label = source.indexOf("// #104 Series Fan")
            (label >= 0) shouldBe true
            source[label + 1] shouldBe "com.omnibus.omnibus.feature.bookdetail.overview.series.SeriesFanSection,"
        }
    }
})
