package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfSummary
import com.omnibus.omnibus.data.shelves.randomShelfSummary
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

private fun manualShelf(owner: Long): ShelfSummary = randomShelfSummary().copy(ownerUserId = owner, kind = ShelfKind.Manual)

private fun smartShelf(owner: Long): ShelfSummary = randomShelfSummary().copy(ownerUserId = owner, kind = ShelfKind.Smart)

private fun shelvesOf(vararg shelves: ShelfSummary, matching: Set<Long> = emptySet()) =
    BookShelves(shelves = shelves.toList(), memberShelfIds = emptySet(), matchingSmartShelfIds = matching)

class ShelfSheetModelTest : DescribeSpec({

    describe("which shelves the sheet lists") {
        it("#116 TC-37, TC-40: keeps only own manual shelves with own matching smart shelves, regardless of being an admin") {
            checkAll(Exhaustive.boolean()) { isAdmin ->
                val user = randomCurrentUser().copy(isAdmin = isAdmin)
                val other = user.id + 1
                val ownManual = manualShelf(user.id)
                val ownMatching = smartShelf(user.id)
                val otherMatching = smartShelf(other)
                val book = shelvesOf(
                    ownManual,
                    manualShelf(other),
                    randomShelfSummary().copy(ownerUserId = user.id, kind = ShelfKind.Wishlist),
                    ownMatching,
                    smartShelf(user.id),
                    otherMatching,
                    matching = setOf(ownMatching.id, otherMatching.id),
                )

                val sheet = buildShelfSheet(book, user, randomString(), emptySet(), emptySet(), emptySet(), emptyMap())

                sheet.manual.map { it.id } shouldBe listOf(ownManual.id)
                sheet.smart.map { it.id } shouldBe listOf(ownMatching.id)
            }
        }
    }

    describe("a manual row") {
        it("#116 TC-38: moves the server's count by this session's toggle, never below zero") {
            data class Case(val on: Boolean, val wasOn: Boolean, val server: Long, val expected: Long)

            checkAll(
                Exhaustive.of(
                    Case(on = false, wasOn = false, server = 3, expected = 3),
                    Case(on = true, wasOn = true, server = 3, expected = 3),
                    Case(on = true, wasOn = false, server = 3, expected = 4),
                    Case(on = false, wasOn = true, server = 3, expected = 2),
                    Case(on = false, wasOn = true, server = 0, expected = 0),
                ),
            ) { case ->
                val user = randomCurrentUser()
                val shelf = manualShelf(user.id).copy(bookCount = case.server)
                val member = if (case.on) setOf(shelf.id) else emptySet()
                val serverMember = if (case.wasOn) setOf(shelf.id) else emptySet()

                val row = buildShelfSheet(shelvesOf(shelf), user, randomString(), member, serverMember, emptySet(), emptyMap())
                    .manual.single()

                row.bookCount shouldBe case.expected
            }
        }

        it("#116 TC-39: stacks at most three covers of other books, in order") {
            val user = randomCurrentUser()
            val uuid = randomString()

            checkAll(
                Exhaustive.of(
                    listOf("a", uuid, "b", "c", "d") to listOf("a", "b", "c"),
                    listOf(uuid) to emptyList<String>(),
                    emptyList<String>() to emptyList<String>(),
                ),
            ) { (covers, expected) ->
                val shelf = manualShelf(user.id).copy(coverUuids = covers)

                buildShelfSheet(shelvesOf(shelf), user, uuid, emptySet(), emptySet(), emptySet(), emptyMap())
                    .manual.single().otherCoverUuids shouldBe expected
            }
        }
    }

    describe("a smart row") {
        it("#116 TC-39: takes its covers without this book, capped at three") {
            val user = randomCurrentUser()
            val uuid = randomString()
            val shelf = smartShelf(user.id).copy(coverUuids = listOf("a", uuid, "b", "c", "d"))

            buildShelfSheet(shelvesOf(shelf, matching = setOf(shelf.id)), user, uuid, emptySet(), emptySet(), emptySet(), emptyMap())
                .smart.single().coverUuids shouldBe listOf("a", "b", "c")
        }

        it("#116 TC-39: carries only the rules already read") {
            val user = randomCurrentUser()
            val rules = ShelfRules(matchMode = ShelfMatchMode.All, rules = emptyList())
            val read = smartShelf(user.id)
            val unread = smartShelf(user.id)
            val book = shelvesOf(read, unread, matching = setOf(read.id, unread.id))

            val rows = buildShelfSheet(book, user, randomString(), emptySet(), emptySet(), emptySet(), mapOf(read.id to rules)).smart

            rows.map { it.rules } shouldBe listOf(rules, null)
        }

        it("#116 TC-55: shows the count with any rule text") {
            checkAll(
                Exhaustive.of(
                    "year is at least 1901" to "14 books · year is at least 1901",
                    null to "14 books",
                ),
            ) { (ruleText, expected) ->
                smartShelfMeta("14 books", ruleText) { count, text -> "$count · $text" } shouldBe expected
            }
        }
    }
})
