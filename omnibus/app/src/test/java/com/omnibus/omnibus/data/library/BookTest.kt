package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun randomCreatorNamed(name: String) = randomCreator().copy(name = name)

class BookTest : DescribeSpec({

    describe("the book's stable identity") {

        context("when the book has a unique identifier") {
            it("TC-20: is that identifier") {
                val identifier = randomString()

                randomBook().copy(uniqueIdentifier = identifier).uuid shouldBe identifier
            }
        }

        context("when the book has no unique identifier") {
            it("TC-20: is the id") {
                val id = randomLong()

                randomBook().copy(id = id, uniqueIdentifier = null).uuid shouldBe id.toString()
            }
        }
    }

    describe("the title to display") {

        context("when the book has a title") {
            it("TC-21: is the title") {
                val title = randomString()

                randomBook().copy(title = title).displayTitle shouldBe title
            }
        }

        context("when there is no usable title") {
            it("TC-21: is the filename") {
                checkAll(Exhaustive.of(null, "", "   ")) { title ->
                    val filename = randomString()

                    randomBook().copy(title = title, filename = filename).displayTitle shouldBe filename
                }
            }
        }
    }

    describe("the authors to display") {

        it("TC-22: joins the creators' names with commas") {
            val creators = listOf(randomCreatorNamed("Ann"), randomCreatorNamed("Ben"))

            randomBook().copy(creators = creators).authorDisplay shouldBe "Ann, Ben"
        }

        context("when a creator's name is blank") {
            it("TC-22: skips it") {
                val creators = listOf(randomCreatorNamed("Ann"), randomCreatorNamed("   "), randomCreatorNamed("Ben"))

                randomBook().copy(creators = creators).authorDisplay shouldBe "Ann, Ben"
            }
        }

        context("when there are no creators") {
            it("TC-22: is Unknown author") {
                randomBook().copy(creators = emptyList()).authorDisplay shouldBe "Unknown author"
            }
        }
    }

    describe("whether the book opens in the EPUB reader") {

        context("when the formats list epub") {
            it("TC-49: is true, whatever the case") {
                checkAll(Exhaustive.of("epub", "EPUB")) { format ->
                    randomBook().copy(formats = listOf(format), bookFiles = emptyList()).hasEpub shouldBe true
                }
            }
        }

        context("when only a file is an epub") {
            it("TC-49: is true") {
                val file = randomBookFile().copy(format = "Epub")

                randomBook().copy(formats = emptyList(), bookFiles = listOf(file)).hasEpub shouldBe true
            }
        }

        context("when the formats are other ebooks") {
            it("TC-49: is false") {
                randomBook().copy(formats = listOf("pdf", "cbz"), bookFiles = emptyList()).hasEpub shouldBe false
            }
        }

        context("when nothing lists a format") {
            it("TC-49: is false") {
                randomBook().copy(formats = emptyList(), bookFiles = emptyList()).hasEpub shouldBe false
            }
        }
    }

    describe("classifying the book's formats") {

        context("when no format is a book format") {
            it("TC-23: counts as neither kind") {
                checkAll(Exhaustive.of(listOf("txt", "docx"), emptyList())) { formats ->
                    val book = randomBook().copy(formats = formats)

                    (book.hasEbook to book.hasAudiobook) shouldBe (false to false)
                }
            }
        }

        context("when the formats include both kinds") {
            it("TC-23: counts as both kinds") {
                val book = randomBook().copy(formats = listOf("epub", "m4b"))

                (book.hasEbook to book.hasAudiobook) shouldBe (true to true)
            }
        }
    }

    describe("the publication year") {

        it("#93 TC-1, TC-2: reads the year from the date formats the server sends") {
            checkAll(Exhaustive.of("1925-04-10" to 1925, "8/4/2015" to 2015)) { (published, year) ->
                randomBook().copy(publishedDate = published).year shouldBe year
            }
        }

        context("when there is no run of four digits") {
            it("#93 TC-3: is unknown") {
                checkAll(Exhaustive.of(null, "unknown", "", "   ", "99", "12345", "١٩٢٥")) { published ->
                    randomBook().copy(publishedDate = published).year.shouldBeNull()
                }
            }
        }

        context("when the text has several runs of digits") {
            it("#93 TC-4: is the first run of exactly four") {
                checkAll(
                    Exhaustive.of("12345 and 1999" to 1999, "Published 1925 (rev. 1999)" to 1925),
                ) { (published, year) ->
                    randomBook().copy(publishedDate = published).year shouldBe year
                }
            }
        }
    }

    describe("whether the book is only on the wishlist") {

        context("when it is wishlisted") {
            context("when it has no files") {
                it("#93 TC-6: is wishlist only") {
                    randomBook().copy(isWishlisted = true, bookFiles = emptyList()).isWishlistOnly shouldBe true
                }
            }

            context("when it has files") {
                it("#93 TC-6: is not wishlist only") {
                    randomBook().copy(isWishlisted = true, bookFiles = listOf(randomBookFile())).isWishlistOnly shouldBe false
                }
            }
        }

        context("when it is not wishlisted") {
            it("#93 TC-6: is not wishlist only") {
                randomBook().copy(isWishlisted = false, bookFiles = emptyList()).isWishlistOnly shouldBe false
            }
        }
    }
})
