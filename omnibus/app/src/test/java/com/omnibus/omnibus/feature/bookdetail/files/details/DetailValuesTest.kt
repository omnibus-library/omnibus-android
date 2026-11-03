package com.omnibus.omnibus.feature.bookdetail.files.details

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.TimeZone

class DetailValuesTest : DescribeSpec({

    val utc = TimeZone.getTimeZone("UTC")
    val pacific = TimeZone.getTimeZone("America/Los_Angeles")

    describe("the Published row") {

        it("#114: a full date reads at day precision") {
            publishedText("1925-04-10", Locale.US) shouldBe "April 10, 1925"
        }

        it("#114: a year and month read at month precision") {
            publishedText("1995-10", Locale.US) shouldBe "October 1995"
        }

        it("#114: a bare year stays a year") {
            publishedText("1890", Locale.US) shouldBe "1890"
        }

        it("#114: a timestamp reads as its date") {
            publishedText("1925-04-10T00:00:00+00:00", Locale.US) shouldBe "April 10, 1925"
        }

        it("#114: text in no known shape is shown as the server sent it") {
            publishedText("8/4/2015", Locale.US) shouldBe "8/4/2015"
            publishedText("  Spring 1901 ", Locale.US) shouldBe "Spring 1901"
        }

        it("#114: an impossible date is shown as sent") {
            publishedText("1925-13-45", Locale.US) shouldBe "1925-13-45"
            publishedText("1925-02-30", Locale.US) shouldBe "1925-02-30"
        }
    }

    describe("the Added row") {

        it("#114: a timestamp reads as a long date with the year") {
            addedText("2026-10-02T09:30:00Z", Locale.US, utc) shouldBe "October 2, 2026"
        }

        it("#114: a timestamp is read in the device zone") {
            addedText("2026-10-02T03:30:00Z", Locale.US, pacific) shouldBe "October 1, 2026"
        }

        it("#114: a bare date is that day in any zone") {
            addedText("2026-10-02", Locale.US, pacific) shouldBe "October 2, 2026"
        }

        it("#114: text that is not a timestamp is shown as sent") {
            addedText("yesterday", Locale.US, utc) shouldBe "yesterday"
        }
    }

    describe("the Language row") {

        fun name(code: String?) = languageName(code, Locale.US, "Unknown")

        it("#114 AC2: a language code is written out as a name") {
            name("en") shouldBe "English"
            name("fr") shouldBe "French"
            name("de") shouldBe "German"
        }

        it("#114 AC2: three-letter codes name the same language") {
            name("eng") shouldBe "English"
            name("fra") shouldBe "French"
            name("fre") shouldBe "French"
            name("ger") shouldBe "German"
            name("deu") shouldBe "German"
        }

        it("#114 AC2: a region or script after the language is ignored") {
            name("en-US") shouldBe "English"
            name("en_GB") shouldBe "English"
            name(" EN ") shouldBe "English"
        }

        it("#114 AC2: the name follows the display locale") {
            languageName("de", Locale.GERMAN, "Unbekannt") shouldBe "Deutsch"
        }

        it("#114: codes that decline to answer read Unknown") {
            listOf("und", "mul", "zxx", "mis").forEach { name(it) shouldBe "Unknown" }
        }

        it("#114: a code no name is known for is shown in capitals") {
            name("qqq") shouldBe "QQQ"
        }

        it("#114: no code makes no row") {
            name(null) shouldBe null
            name("") shouldBe null
            name("  ") shouldBe null
        }
    }
})
