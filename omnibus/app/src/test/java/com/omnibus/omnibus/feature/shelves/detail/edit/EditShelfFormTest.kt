package com.omnibus.omnibus.feature.shelves.detail.edit

import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.acceptedOps
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

/** The ops the rule text and the server accept for [field], as the editor lists them. */
private fun expectedOps(field: ShelfRuleField): List<ShelfRuleOp> = when (field) {
    ShelfRuleField.Tag, ShelfRuleField.Genre, ShelfRuleField.Author, ShelfRuleField.Series ->
        listOf(ShelfRuleOp.Is, ShelfRuleOp.IsNot, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)
    ShelfRuleField.Rating, ShelfRuleField.Year -> listOf(ShelfRuleOp.Is, ShelfRuleOp.Gte)
    ShelfRuleField.Status -> listOf(ShelfRuleOp.Is)
    ShelfRuleField.Format -> listOf(ShelfRuleOp.Includes, ShelfRuleOp.Contains, ShelfRuleOp.StartsWith)
    ShelfRuleField.DateAdded, ShelfRuleField.DateUpdated ->
        listOf(ShelfRuleOp.InLast, ShelfRuleOp.Between, ShelfRuleOp.Before, ShelfRuleOp.After)
}

class EditShelfFormTest : DescribeSpec({

    describe("the ops each field accepts") {

        it("#232 TC-30: accepts the ops the rule text reads for each of the ten fields") {
            checkAll(Exhaustive.enum<ShelfRuleField>()) { field ->
                field.acceptedOps shouldBe expectedOps(field)
            }
        }
    }

    describe("changing a condition's field") {

        it("#232 TC-30: keeps the op when the new field accepts it") {
            EditableRule(id = 1, field = ShelfRuleField.Tag, op = ShelfRuleOp.IsNot, value = "x")
                .withField(ShelfRuleField.Genre).op shouldBe ShelfRuleOp.IsNot
        }

        it("#232 TC-30: resets the op to the field's first accepted op when the new field doesn't accept it") {
            EditableRule(id = 1, field = ShelfRuleField.Tag, op = ShelfRuleOp.Contains, value = "x")
                .withField(ShelfRuleField.Rating).op shouldBe ShelfRuleOp.Is
        }
    }

    describe("a valid condition") {

        it("#232 TC-30: needs a value that is not blank") {
            listOf(EditableRule(id = 1, field = ShelfRuleField.Tag, op = ShelfRuleOp.Is, value = "   ")).valid().shouldBeEmpty()
        }

        it("#232 TC-30: needs an op the field accepts") {
            listOf(EditableRule(id = 1, field = ShelfRuleField.Rating, op = ShelfRuleOp.Contains, value = "5")).valid()
                .shouldBeEmpty()
        }

        it("#232 TC-30: is sent with its value trimmed") {
            listOf(EditableRule(id = 1, field = ShelfRuleField.Tag, op = ShelfRuleOp.Is, value = " x ")).valid() shouldBe
                listOf(ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "x"))
        }
    }

    describe("adding a condition") {

        it("#232 TC-30: appends Tag, is, and an empty value") {
            newRule(3) shouldBe EditableRule(id = 3, field = ShelfRuleField.Tag, op = ShelfRuleOp.Is, value = "")
        }
    }

    describe("conditions with a fixed set of values") {

        it("#232 TC-30: offers a menu for a status, a rating and a format the shelf has a file in, and not for free text") {
            fixedValuesOf(ShelfRuleField.Status, ShelfRuleOp.Is) shouldBe listOf("unread", "reading", "finished")
            fixedValuesOf(ShelfRuleField.Rating, ShelfRuleOp.Gte)?.let { it.first() to it.last() to it.size } shouldBe
                (("0.5" to "5") to 10)
            fixedValuesOf(ShelfRuleField.Format, ShelfRuleOp.Includes)?.contains("epub") shouldBe true
            fixedValuesOf(ShelfRuleField.Format, ShelfRuleOp.Contains) shouldBe null
            checkAll(Exhaustive.of(ShelfRuleField.Tag, ShelfRuleField.Year, ShelfRuleField.DateAdded)) { field ->
                fixedValuesOf(field, field.acceptedOps.first()) shouldBe null
            }
        }

        it("#232 TC-30: reads a value as the menu spells it, ignoring case and how a rating is written") {
            canonicalValue(fixedValuesOf(ShelfRuleField.Status, ShelfRuleOp.Is)!!, "Reading") shouldBe "reading"
            canonicalValue(fixedValuesOf(ShelfRuleField.Rating, ShelfRuleOp.Is)!!, "4.0") shouldBe "4"
            canonicalValue(fixedValuesOf(ShelfRuleField.Rating, ShelfRuleOp.Is)!!, "3.7") shouldBe null
        }

        it("#232 TC-30: drops a value the new field's menu does not offer, and keeps one it does") {
            val status = EditableRule(1, ShelfRuleField.Status, ShelfRuleOp.Is, "reading")

            status.withField(ShelfRuleField.Rating).value shouldBe ""
            EditableRule(1, ShelfRuleField.Tag, ShelfRuleOp.Is, "mood").withField(ShelfRuleField.Status).value shouldBe ""
            EditableRule(1, ShelfRuleField.Tag, ShelfRuleOp.Is, "Reading").withField(ShelfRuleField.Status).value shouldBe "reading"
            EditableRule(1, ShelfRuleField.Rating, ShelfRuleOp.Is, "4").withField(ShelfRuleField.Rating).value shouldBe "4"
        }

        it("#232 TC-30: drops a menu value that means nothing as free text, and keeps free text between free-text fields") {
            EditableRule(1, ShelfRuleField.Rating, ShelfRuleOp.Is, "4").withField(ShelfRuleField.Year).value shouldBe ""
            EditableRule(1, ShelfRuleField.Tag, ShelfRuleOp.Is, "mood").withField(ShelfRuleField.Genre).value shouldBe "mood"
        }

        it("#232 TC-30: drops a format when the op moves to free text, and keeps it when it moves to a menu op") {
            EditableRule(1, ShelfRuleField.Format, ShelfRuleOp.Includes, "epub").withOp(ShelfRuleOp.Contains).value shouldBe ""
            EditableRule(1, ShelfRuleField.Format, ShelfRuleOp.Contains, "epu").withOp(ShelfRuleOp.Includes).value shouldBe ""
            EditableRule(1, ShelfRuleField.Format, ShelfRuleOp.Contains, "epub").withOp(ShelfRuleOp.Includes).value shouldBe "epub"
        }
    }
})
