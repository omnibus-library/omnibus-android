package com.omnibus.omnibus.data.bookmarks

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** A surrogate pair: one code point, two UTF-16 units. */
private const val ASTRAL = "📖"

class BookmarkDraftTest : DescribeSpec({

    describe("creating a bookmark draft") {
        context("when the position has whitespace around it") {
            it("TC-1: trims the position") {
                val position = randomString()

                BookmarkDraft.create(randomString(), "  $position\n", randomString())?.position shouldBe position
            }
        }

        context("when the chapter title has whitespace around it") {
            it("TC-1: trims the title") {
                val title = randomString()

                BookmarkDraft.create(randomString(), randomString(), "  $title ")?.title shouldBe title
            }
        }

        context("when the position is missing") {
            it("TC-2: gives no draft for null, empty and whitespace positions") {
                checkAll(Exhaustive.of(null, "", "   \t")) { position ->
                    BookmarkDraft.create(randomString(), position, randomString()).shouldBeNull()
                }
            }
        }

        context("when the position is exactly 4096 code points") {
            it("TC-3: keeps it whole") {
                val position = "a".repeat(4096)

                BookmarkDraft.create(randomString(), position, null)?.position shouldBe position
            }
        }

        context("when the position is 4097 code points") {
            it("TC-3: gives no draft") {
                BookmarkDraft.create(randomString(), "a".repeat(4097), null).shouldBeNull()
            }
        }

        context("when the position is 4096 astral characters") {
            it("TC-4: counts code points rather than UTF-16 units") {
                val position = ASTRAL.repeat(4096)

                BookmarkDraft.create(randomString(), position, null)?.position shouldBe position
            }
        }

        context("when the title is missing") {
            it("TC-5: makes a draft with a null title for null, empty and whitespace titles") {
                checkAll(Exhaustive.of(null, "", "   ")) { title ->
                    BookmarkDraft.create(randomString(), randomString(), title).shouldNotBeNull().title.shouldBeNull()
                }
            }
        }

        context("when the title is exactly 512 code points") {
            it("TC-6: keeps it whole") {
                val title = "t".repeat(512)

                BookmarkDraft.create(randomString(), randomString(), title)?.title shouldBe title
            }
        }

        context("when the title is 513 code points") {
            it("TC-6: cuts it to its first 512") {
                val first512 = "t".repeat(512)

                BookmarkDraft.create(randomString(), randomString(), first512 + "x")?.title shouldBe first512
            }
        }

        context("when the 512th code point of a long title is a surrogate pair") {
            it("TC-7: keeps the pair whole") {
                val first512 = "a".repeat(511) + ASTRAL

                BookmarkDraft.create(randomString(), randomString(), first512 + "tail")?.title shouldBe first512
            }
        }

        context("when the book uuid is blank") {
            it("TC-8: gives no draft for empty and whitespace uuids") {
                checkAll(Exhaustive.of("", "   ")) { uuid ->
                    BookmarkDraft.create(uuid, randomString(), randomString()).shouldBeNull()
                }
            }
        }
    }
})
