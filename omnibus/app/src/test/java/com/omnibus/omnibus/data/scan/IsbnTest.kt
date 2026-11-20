package com.omnibus.omnibus.data.scan

import com.omnibus.omnibus.testing.TEST_RANDOM
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class IsbnTest : DescribeSpec({

    describe("recognising a book barcode") {
        it("#317 TC-7: accepts a 13-digit barcode starting 978 or 979 with a valid check digit") {
            checkAll(arbitrary { randomValidIsbn13() }) { isbn ->
                isBookEan13(isbn) shouldBe true
            }
        }

        it("#317 TC-7: rejects a 978 or 979 barcode whose check digit is wrong") {
            checkAll(arbitrary { randomValidIsbn13() }) { isbn ->
                val wrongCheck = (isbn.last() - '0' + TEST_RANDOM.nextInt(1, 10)) % 10
                isBookEan13(isbn.dropLast(1) + wrongCheck) shouldBe false
            }
        }

        it("#317 TC-7: rejects a 13-digit barcode with a valid check digit whose prefix isn't 978 or 979") {
            checkAll(Exhaustive.collection(listOf("977", "980", "000", "100"))) { prefix ->
                val body = prefix + randomDigits(9)
                isBookEan13(isbn13WithCheckDigit(body)) shouldBe false
            }
        }

        it("#317 TC-7: rejects a value that isn't 13 digits long") {
            checkAll(Exhaustive.collection(listOf(12, 14))) { length ->
                isBookEan13("978" + randomDigits(length - 3)) shouldBe false
            }
        }

        it("#317 TC-7: rejects a 13-character value with a non-digit") {
            checkAll(arbitrary { randomValidIsbn13() }) { isbn ->
                isBookEan13(isbn.replaceRange(5, 6, "A")) shouldBe false
            }
        }
    }

    describe("checking a typed ISBN") {
        it("#317 TC-8: ignores hyphens and spaces when cleaning") {
            cleanIsbn("978-0 306-40615 7") shouldBe "9780306406157"
        }

        it("#317 TC-8: accepts a valid ISBN-13") {
            checkAll(arbitrary { randomValidIsbn13() }) { isbn ->
                isbnRejection(cleanIsbn(isbn)).shouldBeNull()
            }
        }

        it("#317 TC-8: accepts a valid ISBN-10") {
            checkAll(arbitrary { randomValidIsbn10() }) { isbn ->
                isbnRejection(cleanIsbn(isbn)).shouldBeNull()
            }
        }

        it("#317 TC-8: accepts a lower-case x as the ISBN-10 check digit") {
            isbnRejection(cleanIsbn("080442957x")).shouldBeNull()
        }

        it("#317 TC-8: rejects a length other than 10 or 13 as WrongLength") {
            checkAll(Exhaustive.collection(listOf(0, 1, 9, 11, 12, 14, 20))) { length ->
                isbnRejection(randomDigits(length)) shouldBe IsbnRejection.WrongLength
            }
        }

        it("#317 TC-8: rejects an ISBN with one digit changed as BadCheckDigit") {
            checkAll(arbitrary { randomValidIsbn13() }) { isbn ->
                val index = TEST_RANDOM.nextInt(isbn.length)
                val changed = (isbn[index] - '0' + TEST_RANDOM.nextInt(1, 10)) % 10
                isbnRejection(isbn.replaceRange(index, index + 1, changed.toString())) shouldBe IsbnRejection.BadCheckDigit
            }
        }

        it("#317 TC-8: rejects an X before the last place as InvalidCharacters") {
            isbnRejection("X000000000") shouldBe IsbnRejection.InvalidCharacters
        }
    }
})
