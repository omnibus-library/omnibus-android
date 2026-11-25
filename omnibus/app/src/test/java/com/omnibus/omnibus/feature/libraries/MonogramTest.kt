package com.omnibus.omnibus.feature.libraries

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class MonogramTest : DescribeSpec({

    describe("the monogram of a name") {
        context("when the name has a letter") {
            it("TC-1: gives the first letter of the name, upper-cased, as one code point") {
                checkAll(
                    Exhaustive.collection(
                        listOf(
                            "home" to "H",
                            "école" to "É",
                            "école" to "É",
                            "ßibliothek" to "ß",
                            "istanbul" to "I",
                            "ıspanak" to "I",
                            "ωmega" to "Ω",
                            "東京" to "東",
                            "42 shelves" to "S",
                            "  --home" to "H",
                            "📚 books" to "B",
                            "𝔄bc" to "𝔄",
                        ),
                    ),
                ) { (name, expected) ->
                    val monogram = monogramOf(name)

                    monogram shouldBe Monogram.Letter(expected)
                    (monogram as Monogram.Letter).letter.codePointCount(0, expected.length) shouldBe 1
                }
            }
        }

        context("when the name has no letter") {
            it("TC-2: gives the Maltese cross") {
                checkAll(
                    Exhaustive.collection(
                        listOf(
                            "192.168.1.5:8080",
                            "[::1]:8080",
                            "12345",
                            "📚📚",
                            "",
                        ),
                    ),
                ) { name ->
                    monogramOf(name) shouldBe Monogram.MalteseCross
                }
            }
        }
    }
})
