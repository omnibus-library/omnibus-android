package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.overviewSections
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** The Readers Also Read section is activated as the last Overview section (issue #106). */
class ReadAlikesActivationTest : DescribeSpec({

    describe("read-alikes slot") {

        it("#106 TC-53: the read-alikes section is activated as the last Overview section") {
            val registered = BookDetailSlots.Registered.overviewSections
            overviewSections.count { it === ReadAlikesSection } shouldBe 1
            registered.count { it === ReadAlikesSection } shouldBe 1
            (overviewSections.last() === ReadAlikesSection) shouldBe true
            (registered.last() === ReadAlikesSection) shouldBe true
        }

        it("#106 TC-54: the 106 line holds the fully qualified section object") {
            val source = File(
                System.getProperty("user.dir"),
                "src/main/java/com/omnibus/omnibus/feature/bookdetail/slots/OverviewSections.kt",
            ).readLines().map { it.trim() }
            val label = source.indexOf("// #106 Readers Also Read")
            (label >= 0) shouldBe true
            source[label + 1] shouldBe "com.omnibus.omnibus.feature.bookdetail.overview.readalikes.ReadAlikesSection,"
        }
    }
})
