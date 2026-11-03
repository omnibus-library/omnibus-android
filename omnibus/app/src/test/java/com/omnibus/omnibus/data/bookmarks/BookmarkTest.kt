package com.omnibus.omnibus.data.bookmarks

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

class BookmarkTest : DescribeSpec({

    /** A bookmark with only the addressing fields set; the rest do not affect pathId or key. */
    fun bookmark(id: Long?, clientId: String?) = Bookmark(
        id = id,
        clientId = clientId,
        bookUuid = "book-1",
        position = "epubcfi(/6)",
        title = null,
        chapterTitle = null,
        fraction = null,
        createdAtSeconds = null,
    )

    describe("Bookmark addressing") {

        // TC-14: the delete path prefers the client id; the identity key does the same so an optimistic row matches its saved one.
        it("TC-14: pathId prefers the client id and falls back to the numeric id") {
            bookmark(id = null, clientId = "client-1").pathId shouldBe "client-1"
            bookmark(id = null, clientId = "client-1").key shouldBe "client-1"

            bookmark(id = 42L, clientId = null).pathId shouldBe "42"
            bookmark(id = 42L, clientId = null).key shouldBe "id:42"

            bookmark(id = 42L, clientId = "client-2").pathId shouldBe "client-2"
            bookmark(id = 42L, clientId = "client-2").key shouldBe "client-2"

            bookmark(id = null, clientId = null).pathId.shouldBeNull()
            bookmark(id = null, clientId = null).key.shouldBeNull()
        }
    }
})
