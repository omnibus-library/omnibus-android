package com.omnibus.omnibus.di

import com.omnibus.omnibus.data.bookmarks.BookmarkDraft
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

class UuidClientIdSourceTest : DescribeSpec({

    describe("minting a client id") {

        it("TC-10: gives a uuid string within the server's limit") {
            val id = UuidClientIdSource().mint()

            UUID.fromString(id).toString() shouldBe id
            id.length shouldBeLessThanOrEqual BookmarkDraft.CLIENT_ID_MAX_LENGTH
        }

        it("TC-10: gives a new id each time") {
            val source = UuidClientIdSource()

            source.mint() shouldNotBe source.mint()
        }
    }
})
