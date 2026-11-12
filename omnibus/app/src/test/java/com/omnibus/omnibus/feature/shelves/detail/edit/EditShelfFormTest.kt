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
})
