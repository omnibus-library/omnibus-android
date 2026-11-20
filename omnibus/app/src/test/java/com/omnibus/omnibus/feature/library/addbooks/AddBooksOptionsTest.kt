package com.omnibus.omnibus.feature.library.addbooks

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean

class AddBooksOptionsTest : DescribeSpec({

    describe("the Add books options") {
        context("with a user who cannot upload") {
            it("#319 TC-1: lists only Scan and Wishlist, enabled, regardless of connectivity") {
                checkAll(Exhaustive.boolean()) { isOffline ->
                    addBooksOptions(canUpload = false, isOffline = isOffline) shouldBe listOf(
                        AddBooksOption(AddBooksOptionKind.Scan, enabled = true),
                        AddBooksOption(AddBooksOptionKind.Wishlist, enabled = true),
                    )
                }
            }
        }

        context("with a user who can upload") {
            it("#319 TC-2: lists Upload first, enabled exactly when online, then Scan and Wishlist, regardless of connectivity") {
                checkAll(Exhaustive.boolean()) { isOffline ->
                    addBooksOptions(canUpload = true, isOffline = isOffline) shouldBe listOf(
                        AddBooksOption(AddBooksOptionKind.Upload, enabled = !isOffline),
                        AddBooksOption(AddBooksOptionKind.Scan, enabled = true),
                        AddBooksOption(AddBooksOptionKind.Wishlist, enabled = true),
                    )
                }
            }
        }
    }
})
