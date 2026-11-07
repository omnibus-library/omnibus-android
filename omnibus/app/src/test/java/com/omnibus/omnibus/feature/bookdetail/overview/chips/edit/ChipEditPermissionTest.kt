package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.auth.randomCurrentUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ChipEditPermissionTest : DescribeSpec({
    describe("who may edit chips") {
        context("when nobody is signed in") {
            it("TC-1: refuses") {
                canEditChips(null) shouldBe false
            }
        }

        context("when the user may not edit") {
            it("TC-1: refuses") {
                canEditChips(randomCurrentUser().copy(canEdit = false, isAdmin = false)) shouldBe false
            }
        }

        context("when the user has an editing role") {
            it("TC-2: allows it for an editor, an administrator or someone who is both") {
                checkAll(Exhaustive.of(true to false, false to true, true to true)) { (canEdit, isAdmin) ->
                    canEditChips(randomCurrentUser().copy(canEdit = canEdit, isAdmin = isAdmin)) shouldBe true
                }
            }
        }
    }
})
