package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfSummary
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ShelfSheetModelTest : DescribeSpec({

    val bookUuid = "book-1"
    val user = CurrentUser(id = 1, canEdit = true, canUpload = false, hiddenFormats = emptySet(), isAdmin = false)
    val admin = user.copy(isAdmin = true)

    fun shelf(
        id: Long,
        kind: ShelfKind = ShelfKind.Manual,
        owner: Long = 1,
        name: String = "Shelf $id",
        count: Long = 0,
        covers: List<String> = emptyList(),
    ) = ShelfSummary(id, owner, kind, name, count, covers)

    describe("Sheet model") {

        it("#116 TC-37: keeps only own manual shelves and own matching smart shelves") {
            val book = BookShelves(
                shelves = listOf(
                    shelf(1, ShelfKind.Manual, owner = 1, name = "Mine A"),
                    shelf(2, ShelfKind.Manual, owner = 2, name = "Theirs"),
                    shelf(3, ShelfKind.Wishlist, owner = 1, name = "Wish"),
                    shelf(4, ShelfKind.Smart, owner = 1, name = "Mine smart matching"),
                    shelf(5, ShelfKind.Smart, owner = 1, name = "Mine smart not matching"),
                    shelf(6, ShelfKind.Smart, owner = 2, name = "Theirs smart matching"),
                    shelf(7, ShelfKind.Manual, owner = 1, name = "Mine B"),
                ),
                memberShelfIds = setOf(1),
                matchingSmartShelfIds = setOf(4, 6),
            )

            val sheet = buildShelfSheet(book, user, bookUuid, setOf(1), setOf(1), emptySet(), emptyMap())

            sheet.manual.map { it.name } shouldBe listOf("Mine A", "Mine B")
            sheet.smart.map { it.name } shouldBe listOf("Mine smart matching")
        }

        it("#116 TC-37: an admin sees only their own shelves, never other users'") {
            val book = BookShelves(
                shelves = listOf(
                    shelf(1, ShelfKind.Manual, owner = 1, name = "Mine"),
                    shelf(2, ShelfKind.Manual, owner = 2, name = "Theirs"),
                    shelf(3, ShelfKind.Smart, owner = 2, name = "Theirs smart"),
                ),
                memberShelfIds = emptySet(),
                matchingSmartShelfIds = setOf(3),
            )

            val sheet = buildShelfSheet(book, admin, bookUuid, emptySet(), emptySet(), emptySet(), emptyMap())

            sheet.manual.map { it.name } shouldBe listOf("Mine")
            sheet.smart shouldBe emptyList()
        }

        describe("bookCount is relative to the server figure") {

            val book = BookShelves(
                shelves = listOf(shelf(1, count = 3), shelf(2, count = 5)),
                memberShelfIds = emptySet(),
                matchingSmartShelfIds = emptySet(),
            )

            fun countOf(id: Long, member: Set<Long>, serverMember: Set<Long>, server: Long): Long {
                val shelves = book.shelves.map { if (it.id == id) it.copy(bookCount = server) else it }
                val sheet = buildShelfSheet(
                    book.copy(shelves = shelves),
                    user,
                    bookUuid,
                    member,
                    serverMember,
                    emptySet(),
                    emptyMap(),
                )
                return sheet.manual.single { it.id == id }.bookCount
            }

            it("#116 TC-38: off on the server and on now gives the server count") {
                countOf(1, member = emptySet(), serverMember = emptySet(), server = 3) shouldBe 3
            }

            it("#116 TC-38: on on the server and on now gives the server count") {
                countOf(1, member = setOf(1), serverMember = setOf(1), server = 3) shouldBe 3
            }

            it("#116 TC-38: turned on this session adds one") {
                countOf(1, member = setOf(1), serverMember = emptySet(), server = 3) shouldBe 4
            }

            it("#116 TC-38: turned off this session takes one away") {
                countOf(1, member = emptySet(), serverMember = setOf(1), server = 3) shouldBe 2
            }

            it("#116 TC-38: the count never goes below zero") {
                countOf(1, member = emptySet(), serverMember = setOf(1), server = 0) shouldBe 0
            }
        }

        it("#116 TC-38: isOn and isPending come from the sets") {
            val book = BookShelves(
                shelves = listOf(shelf(1, count = 2), shelf(2, count = 2)),
                memberShelfIds = emptySet(),
                matchingSmartShelfIds = emptySet(),
            )

            val sheet = buildShelfSheet(book, user, bookUuid, setOf(1), emptySet(), setOf(1), emptyMap())

            sheet.manual.single { it.id == 1L }.let {
                it.isOn shouldBe true
                it.isPending shouldBe true
            }
            sheet.manual.single { it.id == 2L }.let {
                it.isOn shouldBe false
                it.isPending shouldBe false
            }
        }

        describe("cover lists") {

            it("#116 TC-39: otherCoverUuids excludes this book, keeps order and takes three") {
                val book = BookShelves(
                    shelves = listOf(shelf(1, covers = listOf("a", bookUuid, "b", "c", "d"))),
                    memberShelfIds = emptySet(),
                    matchingSmartShelfIds = emptySet(),
                )

                val row = buildShelfSheet(book, user, bookUuid, emptySet(), emptySet(), emptySet(), emptyMap())
                    .manual.single()

                row.otherCoverUuids shouldBe listOf("a", "b", "c")
            }

            it("#116 TC-39: empty cover_uuids gives an empty stack") {
                val book = BookShelves(
                    shelves = listOf(shelf(1)),
                    memberShelfIds = emptySet(),
                    matchingSmartShelfIds = emptySet(),
                )

                buildShelfSheet(book, user, bookUuid, emptySet(), emptySet(), emptySet(), emptyMap())
                    .manual.single().otherCoverUuids shouldBe emptyList()
            }

            it("#116 TC-39: when this book is the only cover the stack is empty") {
                val book = BookShelves(
                    shelves = listOf(shelf(1, covers = listOf(bookUuid))),
                    memberShelfIds = emptySet(),
                    matchingSmartShelfIds = emptySet(),
                )

                buildShelfSheet(book, user, bookUuid, emptySet(), emptySet(), emptySet(), emptyMap())
                    .manual.single().otherCoverUuids shouldBe emptyList()
            }

            it("#116 TC-39: smart rows drop this book and cap at three, and take rules from the map") {
                val rules = ShelfRules(matchMode = ShelfMatchMode.All, rules = emptyList())
                val book = BookShelves(
                    shelves = listOf(
                        shelf(4, ShelfKind.Smart, covers = listOf("a", bookUuid, "b", "c", "d")),
                        shelf(5, ShelfKind.Smart, covers = listOf("x")),
                    ),
                    memberShelfIds = emptySet(),
                    matchingSmartShelfIds = setOf(4, 5),
                )

                val smart = buildShelfSheet(book, user, bookUuid, emptySet(), emptySet(), emptySet(), mapOf(4L to rules))
                    .smart

                smart.single { it.id == 4L }.let {
                    it.coverUuids shouldBe listOf("a", "b", "c")
                    it.rules shouldBe rules
                }
                smart.single { it.id == 5L }.rules shouldBe null
            }
        }

        it("#116 TC-40: no owned manual shelf gives an empty manual list but keeps smart rows") {
            val book = BookShelves(
                shelves = listOf(
                    shelf(1, ShelfKind.Manual, owner = 2),
                    shelf(2, ShelfKind.Wishlist),
                    shelf(3, ShelfKind.Smart, owner = 1),
                ),
                memberShelfIds = emptySet(),
                matchingSmartShelfIds = setOf(3),
            )

            val sheet = buildShelfSheet(book, user, bookUuid, emptySet(), emptySet(), emptySet(), emptyMap())

            sheet.manual.isEmpty() shouldBe true
            sheet.smart.map { it.id } shouldBe listOf(3L)
        }

        describe("smart meta") {

            it("#116 TC-55: uses the rule text when there is one") {
                smartShelfMeta("14 books", "year is at least 1901") { a, b -> "$a · $b" } shouldBe
                    "14 books · year is at least 1901"
            }

            it("#116 TC-55: shows the count alone without rule text") {
                smartShelfMeta("3 books", null) { a, b -> "$a · $b" } shouldBe "3 books"
            }
        }
    }
})
