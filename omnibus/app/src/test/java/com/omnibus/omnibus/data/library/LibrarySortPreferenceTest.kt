package com.omnibus.omnibus.data.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LibrarySortPreferenceTest : DescribeSpec({

    describe("decode") {

        it("TC-111: a valid sort and direction decode to themselves") {
            LibrarySortPreference.decode("author", "asc") shouldBe
                LibrarySortPreference(LibrarySort.Author, SortDirection.Asc)
        }

        it("TC-112: an unknown or missing sort falls back to NewestAdded and keeps a valid direction") {
            LibrarySortPreference.decode("bogus", "asc") shouldBe
                LibrarySortPreference(LibrarySort.NewestAdded, SortDirection.Asc)
            LibrarySortPreference.decode(null, "asc") shouldBe
                LibrarySortPreference(LibrarySort.NewestAdded, SortDirection.Asc)
        }

        it("TC-113: an unknown or missing direction falls back to Desc and keeps a valid sort") {
            LibrarySortPreference.decode("title", "sideways") shouldBe
                LibrarySortPreference(LibrarySort.Title, SortDirection.Desc)
            LibrarySortPreference.decode("title", null) shouldBe
                LibrarySortPreference(LibrarySort.Title, SortDirection.Desc)
        }

        it("TC-114: both missing or both unknown decode to the defaults") {
            LibrarySortPreference.decode(null, null) shouldBe LibrarySortPreference()
            LibrarySortPreference.decode("bogus", "bogus") shouldBe LibrarySortPreference()
            LibrarySortPreference() shouldBe
                LibrarySortPreference(LibrarySort.NewestAdded, SortDirection.Desc)
        }
    }
})
