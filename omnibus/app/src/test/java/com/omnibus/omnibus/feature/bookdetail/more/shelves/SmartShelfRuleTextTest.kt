package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.testing.randomEnum
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** English words, as the strings file has them. */
private val english = RuleWords(
    field = { field -> field.wire },
    op = { op, value ->
        when (op) {
            ShelfRuleOp.Gte -> "is at least $value"
            ShelfRuleOp.InLast -> "in the last $value"
            else -> "${op.wire} $value"
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

private fun rule(field: ShelfRuleField?, op: ShelfRuleOp?, value: String) = ShelfRule(field, op, value)

private fun summary(mode: ShelfMatchMode?, vararg rules: ShelfRule): String? =
    smartShelfRuleText(ShelfRules(mode, rules.toList()), english)

private val tagIsA = rule(ShelfRuleField.Tag, ShelfRuleOp.Is, "A")
private val authorIsB = rule(ShelfRuleField.Author, ShelfRuleOp.Is, "B")
private val yearIsC = rule(ShelfRuleField.Year, ShelfRuleOp.Is, "1901")

class SmartShelfRuleTextTest : DescribeSpec({

    describe("summarising a smart shelf's rules") {
        context("when there is one rule") {
            it("#116 TC-51, TC-53: reads its clause, with the value trimmed") {
                checkAll(
                    Exhaustive.of(
                        rule(ShelfRuleField.Year, ShelfRuleOp.Gte, "  1901 ") to "${ShelfRuleField.Year.wire} is at least 1901",
                        rule(ShelfRuleField.Status, ShelfRuleOp.Is, "reading") to "${ShelfRuleField.Status.wire} ${ShelfRuleOp.Is.wire} Reading",
                        rule(ShelfRuleField.DateAdded, ShelfRuleOp.InLast, "2w") to "${ShelfRuleField.DateAdded.wire} in the last 2 weeks",
                        rule(ShelfRuleField.DateAdded, ShelfRuleOp.InLast, "1d") to "${ShelfRuleField.DateAdded.wire} in the last 1 day",
                        rule(ShelfRuleField.DateUpdated, ShelfRuleOp.Between, "2020-01-01..2021-01-01") to
                            "${ShelfRuleField.DateUpdated.wire} between 2020-01-01 and 2021-01-01",
                    ),
                ) { (rule, expected) ->
                    summary(randomEnum<ShelfMatchMode>(), rule) shouldBe expected
                }
            }
        }

        context("when there are several rules") {
            it("#116 TC-52: combines two by the match mode, with a missing mode as all") {
                val tag = "${ShelfRuleField.Tag.wire} ${ShelfRuleOp.Is.wire} A"
                val author = "${ShelfRuleField.Author.wire} ${ShelfRuleOp.Is.wire} B"

                checkAll(
                    Exhaustive.of(
                        ShelfMatchMode.All to "$tag and $author",
                        ShelfMatchMode.Any to "$tag or $author",
                        null to "$tag and $author",
                    ),
                ) { (mode, expected) ->
                    summary(mode, tagIsA, authorIsB) shouldBe expected
                }
            }

            it("#116 TC-52: reads three or more as the first plus a count") {
                val tag = "${ShelfRuleField.Tag.wire} ${ShelfRuleOp.Is.wire} A"

                summary(randomEnum<ShelfMatchMode>(), tagIsA, authorIsB, yearIsC) shouldBe "$tag + 2 more"
                summary(randomEnum<ShelfMatchMode>(), tagIsA, authorIsB, yearIsC, tagIsA, authorIsB) shouldBe "$tag + 4 more"
            }
        }

        context("when a rule can't be summarised") {
            it("#116 TC-53: gives no text at all") {
                checkAll(
                    Exhaustive.of(
                        rule(null, ShelfRuleOp.Is, "x"),
                        rule(ShelfRuleField.Tag, null, "x"),
                        rule(ShelfRuleField.Tag, ShelfRuleOp.Is, "   "),
                        rule(ShelfRuleField.Status, ShelfRuleOp.Is, "abandoned"),
                        rule(ShelfRuleField.DateAdded, ShelfRuleOp.InLast, "7"),
                        rule(ShelfRuleField.DateAdded, ShelfRuleOp.InLast, "7x"),
                        rule(ShelfRuleField.DateAdded, ShelfRuleOp.InLast, "0d"),
                        rule(ShelfRuleField.DateUpdated, ShelfRuleOp.Between, "2020"),
                        rule(ShelfRuleField.DateUpdated, ShelfRuleOp.Between, "..2021"),
                        rule(ShelfRuleField.DateUpdated, ShelfRuleOp.Between, "a..b..c"),
                    ),
                ) { rule ->
                    summary(randomEnum<ShelfMatchMode>(), tagIsA, rule) shouldBe null
                }
            }
        }

        context("when there are no rules") {
            it("#116 TC-53: gives no text") {
                summary(randomEnum<ShelfMatchMode>()) shouldBe null
            }
        }

        it("#116 TC-51, TC-54: summarises only the field-op pairs the server accepts") {
            val summarised = ShelfRuleField.entries.flatMap { field ->
                ShelfRuleOp.entries.map { op -> field to op }
            }.filter { (field, op) ->
                val value = when {
                    field == ShelfRuleField.Status -> "reading"
                    op == ShelfRuleOp.InLast -> "2w"
                    op == ShelfRuleOp.Between -> "2020-01-01..2021-01-01"
                    else -> "x"
                }
                summary(null, rule(field, op, value)) != null
            }.toSet()

            val textOps = listOf(ShelfRuleOp.Is, ShelfRuleOp.IsNot, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)
            val dateOps = listOf(ShelfRuleOp.InLast, ShelfRuleOp.Between, ShelfRuleOp.Before, ShelfRuleOp.After)
            val numberOps = listOf(ShelfRuleOp.Is, ShelfRuleOp.Gte)
            val formatOps = listOf(ShelfRuleOp.Includes, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)

            summarised shouldBe buildSet {
                listOf(ShelfRuleField.Tag, ShelfRuleField.Genre, ShelfRuleField.Author, ShelfRuleField.Series).forEach { field ->
                    textOps.forEach { add(field to it) }
                }
                numberOps.forEach {
                    add(ShelfRuleField.Rating to it)
                    add(ShelfRuleField.Year to it)
                }
                add(ShelfRuleField.Status to ShelfRuleOp.Is)
                formatOps.forEach { add(ShelfRuleField.Format to it) }
                listOf(ShelfRuleField.DateAdded, ShelfRuleField.DateUpdated).forEach { field ->
                    dateOps.forEach { add(field to it) }
                }
            }
        }
    }
})
