package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class SmartShelfRuleTextTest : DescribeSpec({

    /** English words, as the strings file has them. */
    val english = RuleWords(
        field = { field ->
            when (field) {
                ShelfRuleField.Tag -> "tag"
                ShelfRuleField.Genre -> "genre"
                ShelfRuleField.Author -> "author"
                ShelfRuleField.Series -> "series"
                ShelfRuleField.Rating -> "rating"
                ShelfRuleField.Status -> "status"
                ShelfRuleField.Format -> "format"
                ShelfRuleField.Year -> "year"
                ShelfRuleField.DateAdded -> "added"
                ShelfRuleField.DateUpdated -> "updated"
            }
        },
        op = { op, value ->
            when (op) {
                ShelfRuleOp.Is -> "is $value"
                ShelfRuleOp.IsNot -> "is not $value"
                ShelfRuleOp.Contains -> "contains $value"
                ShelfRuleOp.StartsWith -> "starts with $value"
                ShelfRuleOp.Gte -> "is at least $value"
                ShelfRuleOp.Includes -> "includes $value"
                ShelfRuleOp.InLast -> "in the last $value"
                ShelfRuleOp.Between -> "between $value"
                ShelfRuleOp.Before -> "before $value"
                ShelfRuleOp.After -> "after $value"
            }
        },
        between = { from, to -> "between $from and $to" },
        inLast = { count, unit ->
            val word = when (unit) {
                'd' -> "day"
                'w' -> "week"
                'm' -> "month"
                else -> "year"
            }
            if (count == 1) "1 $word" else "$count ${word}s"
        },
        status = { value ->
            when (value) {
                "unread" -> "Unread"
                "reading" -> "Reading"
                "finished" -> "Finished"
                else -> null
            }
        },
        clause = { field, op -> "$field $op" },
        and = { first, second -> "$first and $second" },
        or = { first, second -> "$first or $second" },
        more = { first, count -> "$first + $count more" },
    )

    fun rule(field: ShelfRuleField?, op: ShelfRuleOp?, value: String) = ShelfRule(field, op, value)

    fun summary(vararg rules: ShelfRule, mode: ShelfMatchMode? = ShelfMatchMode.All): String? =
        smartShelfRuleText(ShelfRules(mode, rules.toList()), english)

    describe("smartShelfRuleText") {

        describe("single rule clause for every valid field and op pair") {

            val textFields = listOf(ShelfRuleField.Tag, ShelfRuleField.Genre, ShelfRuleField.Author, ShelfRuleField.Series)
            for (field in textFields) {
                val expected = mapOf(
                    ShelfRuleOp.Is to "is Le Guin",
                    ShelfRuleOp.IsNot to "is not Le Guin",
                    ShelfRuleOp.Contains to "contains Le Guin",
                    ShelfRuleOp.StartsWith to "starts with Le Guin",
                )
                for ((op, phrase) in expected) {
                    it("#116 TC-51: ${field.wire} ${op.wire}") {
                        summary(rule(field, op, "Le Guin"), mode = null) shouldBe "${english.field(field)} $phrase"
                    }
                }
            }

            it("#116 TC-51: rating is") {
                summary(rule(ShelfRuleField.Rating, ShelfRuleOp.Is, "4.5"), mode = null) shouldBe "rating is 4.5"
            }
            it("#116 TC-51: rating gte") {
                summary(rule(ShelfRuleField.Rating, ShelfRuleOp.Gte, "3"), mode = null) shouldBe "rating is at least 3"
            }

            listOf("unread" to "Unread", "reading" to "Reading", "finished" to "Finished").forEach { (token, word) ->
                it("#116 TC-51: status is $token") {
                    summary(rule(ShelfRuleField.Status, ShelfRuleOp.Is, token), mode = null) shouldBe "status is $word"
                }
            }

            it("#116 TC-51: format includes") {
                summary(rule(ShelfRuleField.Format, ShelfRuleOp.Includes, "epub"), mode = null) shouldBe "format includes epub"
            }
            it("#116 TC-51: format contains") {
                summary(rule(ShelfRuleField.Format, ShelfRuleOp.Contains, "epub"), mode = null) shouldBe "format contains epub"
            }
            it("#116 TC-51: format starts_with") {
                summary(rule(ShelfRuleField.Format, ShelfRuleOp.StartsWith, "ep"), mode = null) shouldBe "format starts with ep"
            }

            it("#116 TC-51: year is") {
                summary(rule(ShelfRuleField.Year, ShelfRuleOp.Is, "1901"), mode = null) shouldBe "year is 1901"
            }
            it("#116 TC-51: year gte") {
                summary(rule(ShelfRuleField.Year, ShelfRuleOp.Gte, "1901"), mode = null) shouldBe "year is at least 1901"
            }

            for (field in listOf(ShelfRuleField.DateAdded, ShelfRuleField.DateUpdated)) {
                val word = if (field == ShelfRuleField.DateAdded) "added" else "updated"

                listOf("7d" to "7 days", "2w" to "2 weeks", "3m" to "3 months", "1y" to "1 year", "1d" to "1 day").forEach { (window, text) ->
                    it("#116 TC-51: ${field.wire} in_last $window") {
                        summary(rule(field, ShelfRuleOp.InLast, window), mode = null) shouldBe "$word in the last $text"
                    }
                }
                it("#116 TC-51: ${field.wire} between") {
                    summary(rule(field, ShelfRuleOp.Between, "2020-01-01..2021-01-01"), mode = null) shouldBe
                        "$word between 2020-01-01 and 2021-01-01"
                }
                it("#116 TC-51: ${field.wire} before") {
                    summary(rule(field, ShelfRuleOp.Before, "2020-01-01"), mode = null) shouldBe "$word before 2020-01-01"
                }
                it("#116 TC-51: ${field.wire} after") {
                    summary(rule(field, ShelfRuleOp.After, "2020-01-01"), mode = null) shouldBe "$word after 2020-01-01"
                }
            }
        }

        describe("combinations") {

            val tagA = rule(ShelfRuleField.Tag, ShelfRuleOp.Is, "A")
            val authorB = rule(ShelfRuleField.Author, ShelfRuleOp.Is, "B")
            val yearC = rule(ShelfRuleField.Year, ShelfRuleOp.Is, "1901")

            it("#116 TC-52: two rules with All read A and B") {
                summary(tagA, authorB, mode = ShelfMatchMode.All) shouldBe "tag is A and author is B"
            }
            it("#116 TC-52: two rules with Any read A or B") {
                summary(tagA, authorB, mode = ShelfMatchMode.Any) shouldBe "tag is A or author is B"
            }
            it("#116 TC-52: three rules read the first and two more") {
                summary(tagA, authorB, yearC, mode = ShelfMatchMode.All) shouldBe "tag is A + 2 more"
            }
            it("#116 TC-52: five rules read the first and four more") {
                summary(tagA, authorB, yearC, tagA, authorB, mode = ShelfMatchMode.Any) shouldBe "tag is A + 4 more"
            }
            it("#116 TC-52: a missing match mode with two rules is treated as All") {
                summary(tagA, authorB, mode = null) shouldBe "tag is A and author is B"
            }
            it("#116 TC-52: a missing match mode with one rule is that clause") {
                summary(yearC, mode = null) shouldBe "year is 1901"
            }
        }

        describe("fallbacks") {

            it("#116 TC-53: empty rules give null") {
                summary(mode = ShelfMatchMode.All) shouldBe null
            }

            it("#116 TC-53: an unknown field gives null") {
                summary(rule(null, ShelfRuleOp.Is, "x")) shouldBe null
            }

            it("#116 TC-53: an unknown op gives null") {
                summary(rule(ShelfRuleField.Tag, null, "x")) shouldBe null
            }

            it("#116 TC-53: pairs the server rejects give null") {
                listOf(
                    ShelfRuleField.Rating to ShelfRuleOp.Contains,
                    ShelfRuleField.Status to ShelfRuleOp.StartsWith,
                    ShelfRuleField.Year to ShelfRuleOp.Contains,
                    ShelfRuleField.Format to ShelfRuleOp.Is,
                    ShelfRuleField.DateAdded to ShelfRuleOp.Is,
                    ShelfRuleField.Tag to ShelfRuleOp.Gte,
                ).forEach { (field, op) ->
                    summary(rule(field, op, "7d"), mode = null) shouldBe null
                }
            }

            it("#116 TC-53: a blank or whitespace value gives null") {
                summary(rule(ShelfRuleField.Tag, ShelfRuleOp.Is, ""), mode = null) shouldBe null
                summary(rule(ShelfRuleField.Tag, ShelfRuleOp.Is, "   "), mode = null) shouldBe null
            }

            it("#116 TC-53: a status outside unread, reading and finished gives null") {
                summary(rule(ShelfRuleField.Status, ShelfRuleOp.Is, "abandoned"), mode = null) shouldBe null
            }

            it("#116 TC-53: a malformed in_last window gives null") {
                listOf("abc", "7", "7x", "-1d", "d", "", "0d").forEach { window ->
                    summary(rule(ShelfRuleField.DateAdded, ShelfRuleOp.InLast, window), mode = null) shouldBe null
                }
            }

            it("#116 TC-53: a malformed between range gives null") {
                listOf("2020", "..2021", "2020..", "a..b..c", "..", " .. ").forEach { range ->
                    summary(rule(ShelfRuleField.DateUpdated, ShelfRuleOp.Between, range), mode = null) shouldBe null
                }
            }

            it("#116 TC-53: one invalid rule makes the whole text null") {
                summary(
                    rule(ShelfRuleField.Tag, ShelfRuleOp.Is, "A"),
                    rule(ShelfRuleField.Year, ShelfRuleOp.Contains, "1901"),
                    mode = ShelfMatchMode.All,
                ) shouldBe null
            }

            it("#116 TC-53: values are trimmed before use") {
                summary(rule(ShelfRuleField.Year, ShelfRuleOp.Gte, "  1901 "), mode = null) shouldBe "year is at least 1901"
            }
        }

        it("#116 TC-54: only the valid field and op pairs summarise") {
            val summarised = mutableSetOf<Pair<ShelfRuleField, ShelfRuleOp>>()
            for (field in ShelfRuleField.entries) {
                for (op in ShelfRuleOp.entries) {
                    val value = when {
                        field == ShelfRuleField.Status -> "reading"
                        op == ShelfRuleOp.InLast -> "2w"
                        op == ShelfRuleOp.Between -> "2020-01-01..2021-01-01"
                        else -> "x"
                    }
                    if (summary(rule(field, op, value), mode = null) != null) summarised += field to op
                }
            }

            val textFields = listOf(ShelfRuleField.Tag, ShelfRuleField.Genre, ShelfRuleField.Author, ShelfRuleField.Series)
            val expected = buildSet {
                textFields.forEach { f ->
                    listOf(ShelfRuleOp.Is, ShelfRuleOp.IsNot, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith).forEach { add(f to it) }
                }
                listOf(ShelfRuleOp.Is, ShelfRuleOp.Gte).forEach { add(ShelfRuleField.Rating to it) }
                add(ShelfRuleField.Status to ShelfRuleOp.Is)
                listOf(ShelfRuleOp.Includes, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith).forEach { add(ShelfRuleField.Format to it) }
                listOf(ShelfRuleOp.Is, ShelfRuleOp.Gte).forEach { add(ShelfRuleField.Year to it) }
                listOf(ShelfRuleField.DateAdded, ShelfRuleField.DateUpdated).forEach { f ->
                    listOf(ShelfRuleOp.InLast, ShelfRuleOp.Between, ShelfRuleOp.Before, ShelfRuleOp.After).forEach { add(f to it) }
                }
            }

            expected.size shouldBe 32
            summarised shouldBe expected
        }
    }
})
