package com.omnibus.omnibus.feature.bookdetail.files.details

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.Locale
import java.util.TimeZone

private val PACIFIC: TimeZone = TimeZone.getTimeZone("America/Los_Angeles")

private fun languageName(code: String?) = languageName(code, Locale.US, "Unknown")

class DetailValuesTest : DescribeSpec({

    describe("the Published row") {

        context("when the date is a full date") {
            it("#114: reads at day precision") {
                publishedText("1925-04-10", Locale.US) shouldBe "April 10, 1925"
            }
        }

        context("when the date is a year with a month") {
            it("#114: reads at month precision") {
                publishedText("1995-10", Locale.US) shouldBe "October 1995"
            }
        }

        context("when the date is a bare year") {
            it("#114: stays a year") {
                publishedText("1890", Locale.US) shouldBe "1890"
            }
        }

        context("when the date is a timestamp") {
            it("#114: reads as its date") {
                publishedText("1925-04-10T00:00:00+00:00", Locale.US) shouldBe "April 10, 1925"
            }
        }

        context("when the date is not one it can read") {
            it("#114: shows it as sent, for an unknown shape or an impossible date") {
                checkAll(Exhaustive.of("8/4/2015", "1925-13-45", "1925-02-30")) { date ->
                    publishedText(date, Locale.US) shouldBe date
                }
            }
        }
    }

    describe("the Added row") {

        context("when the value is a timestamp") {
            it("#114: reads in the device zone") {
                addedText("2026-10-02T03:30:00Z", Locale.US, PACIFIC) shouldBe "October 1, 2026"
            }
        }

        context("when the value is a bare date") {
            it("#114: is that day in any zone") {
                addedText("2026-10-02", Locale.US, PACIFIC) shouldBe "October 2, 2026"
            }
        }

        context("when the value is not a timestamp") {
            it("#114: shows it as sent") {
                val utc: TimeZone = TimeZone.getTimeZone("UTC")

                addedText("yesterday", Locale.US, utc) shouldBe "yesterday"
            }
        }
    }

    describe("the Language row") {

        context("when the code has two letters") {
            it("#114 AC2: writes out the name") {
                languageName("fr") shouldBe "French"
            }
        }

        context("when the code has three letters") {
            it("#114 AC2: names the same language, for bibliographic or terminology codes") {
                checkAll(Exhaustive.of("ger", "deu")) { code ->
                    languageName(code) shouldBe "German"
                }
            }
        }

        context("when the code is not a bare language") {
            it("#114 AC2: ignores all but the language") {
                checkAll(Exhaustive.of("en-US", "en_GB", " EN ")) { code ->
                    languageName(code) shouldBe "English"
                }
            }
        }

        context("when the code declines to name a language") {
            it("#114: reads Unknown") {
                checkAll(Exhaustive.of("und", "mul", "zxx", "mis")) { code ->
                    languageName(code) shouldBe "Unknown"
                }
            }
        }

        context("when no name is known for the code") {
            it("#114: shows it in capitals") {
                languageName("qqq") shouldBe "QQQ"
            }
        }

        context("when there is no code") {
            it("#114: makes no row") {
                checkAll(Exhaustive.of(null, "", "  ")) { code ->
                    languageName(code) shouldBe null
                }
            }
        }
    }
})
