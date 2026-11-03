package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import com.omnibus.omnibus.data.auth.CurrentUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ChipEditPermissionTest : DescribeSpec({

    fun user(canEdit: Boolean, isAdmin: Boolean) =
        CurrentUser(id = 1, canEdit = canEdit, canUpload = false, hiddenFormats = emptySet(), isAdmin = isAdmin)

    describe("canEditChips") {

        it("TC-1: is false for null and for a user with neither flag") {
            canEditChips(null) shouldBe false
            canEditChips(user(canEdit = false, isAdmin = false)) shouldBe false
        }

        it("TC-2: is true for canEdit alone and for isAdmin alone") {
            canEditChips(user(canEdit = true, isAdmin = false)) shouldBe true
            canEditChips(user(canEdit = false, isAdmin = true)) shouldBe true
        }

        it("TC-3: is true when both flags are set") {
            canEditChips(user(canEdit = true, isAdmin = true)) shouldBe true
        }
    }
})
