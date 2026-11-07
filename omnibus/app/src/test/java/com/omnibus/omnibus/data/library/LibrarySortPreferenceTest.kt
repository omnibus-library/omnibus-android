package com.omnibus.omnibus.data.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

class LibrarySortPreferenceTest : DescribeSpec({

    describe("decoding the stored tokens") {

        it("TC-111: reads back every sort and direction") {
            checkAll(Exhaustive.enum<LibrarySort>(), Exhaustive.enum<SortDirection>()) { sort, direction ->
                LibrarySortPreference.decode(sort.apiName, direction.apiName) shouldBe
                    LibrarySortPreference(sort, direction)
            }
        }

        context("when the sort is not a known token") {
            it("TC-112: falls back to NewestAdded, keeping a valid direction") {
                checkAll(Exhaustive.of("bogus", "", "Title", " title", null)) { rawSort ->
                    LibrarySortPreference.decode(rawSort, "asc") shouldBe
                        LibrarySortPreference(LibrarySort.NewestAdded, SortDirection.Asc)
                }
            }
        }

        context("when the direction is not a known token") {
            it("TC-113: falls back to Desc, keeping a valid sort") {
                checkAll(Exhaustive.of("sideways", "", "ASC", null)) { rawDirection ->
                    LibrarySortPreference.decode("title", rawDirection) shouldBe
                        LibrarySortPreference(LibrarySort.Title, SortDirection.Desc)
                }
            }
        }
    }
})
