package com.omnibus.omnibus.data.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class LibraryRepositoryTypesTest : DescribeSpec({

    describe("fromApiName") {

        it("TC-109: maps every known sort and direction token back to its value") {
            LibrarySort.entries.forEach { LibrarySort.fromApiName(it.apiName) shouldBe it }
            SortDirection.entries.forEach { SortDirection.fromApiName(it.apiName) shouldBe it }
        }

        it("TC-109: returns null for unknown, empty, null and wrong-case tokens") {
            listOf("bogus", "", "Title", "TITLE", "recently-interacted", " title", null).forEach {
                LibrarySort.fromApiName(it).shouldBeNull()
                SortDirection.fromApiName(it).shouldBeNull()
            }
            SortDirection.fromApiName("ASC").shouldBeNull()
        }
    }

    describe("SortDirection.toggled") {

        it("TC-110: flips Asc to Desc and Desc to Asc") {
            SortDirection.Asc.toggled() shouldBe SortDirection.Desc
            SortDirection.Desc.toggled() shouldBe SortDirection.Asc
        }
    }
})
