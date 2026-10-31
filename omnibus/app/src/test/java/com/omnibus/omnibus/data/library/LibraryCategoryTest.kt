package com.omnibus.omnibus.data.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LibraryCategoryTest : DescribeSpec({

    describe("toQuery") {

        it("TC-108: the wire tokens are the server's sort and direction values") {
            LibrarySort.entries.map { it.apiName } shouldBe listOf(
                "title",
                "author",
                "series",
                "recently_interacted",
                "last_updated",
                "newest_added",
            )
            SortDirection.entries.map { it.apiName } shouldBe listOf("asc", "desc")
        }
    }
})
