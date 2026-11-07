package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.bookmarks.randomBookmark
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomRelocateData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReaderUIStateBookmarksTest : DescribeSpec({

    fun stateAt(cfi: String?, bookmarkPosition: String) = ReaderUIState(
        location = randomRelocateData().copy(cfi = cfi),
        bookmarks = listOf(randomBookmark().copy(position = bookmarkPosition)),
    )

    describe("whether the current page is bookmarked") {
        context("with a bookmark at the location") {
            it("TC-41: is bookmarked, with whitespace on either position") {
                val cfi = randomCfi()

                checkAll(Exhaustive.of(cfi to cfi, "  $cfi  " to cfi, cfi to "  $cfi\n")) { (location, position) ->
                    stateAt(location, position).isPageBookmarked shouldBe true
                }
            }
        }

        context("with a bookmark elsewhere") {
            it("TC-41: is not bookmarked") {
                stateAt(randomCfi(), randomCfi()).isPageBookmarked shouldBe false
            }
        }

        context("without a location cfi") {
            it("TC-41: is not bookmarked, for a null, empty or blank cfi") {
                checkAll(Exhaustive.of(null to "", "" to "", "   " to "   ")) { (location, position) ->
                    stateAt(location, position).isPageBookmarked shouldBe false
                }
            }
        }
    }
})
