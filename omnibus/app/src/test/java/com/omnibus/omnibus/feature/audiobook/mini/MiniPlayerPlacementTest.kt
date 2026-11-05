package com.omnibus.omnibus.feature.audiobook.mini

import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.shell.LibraryRoot
import com.omnibus.omnibus.feature.shell.SearchRoot
import com.omnibus.omnibus.feature.shell.StatsRoot
import com.omnibus.omnibus.feature.shell.YouRoot
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MiniPlayerPlacementTest : DescribeSpec({

    describe("shellHidesMiniPlayer") {

        // P-MINI-1
        it("#172 TC-13: Book Detail on top hides the bar") {
            shellHidesMiniPlayer(BookDetailRoute("u")) shouldBe true
        }

        // P-MINI-1
        it("#172 TC-14: the tab roots, the Book Detail placeholder and an empty stack show the bar") {
            shellHidesMiniPlayer(LibraryRoot) shouldBe false
            shellHidesMiniPlayer(SearchRoot) shouldBe false
            shellHidesMiniPlayer(StatsRoot) shouldBe false
            shellHidesMiniPlayer(YouRoot) shouldBe false
            shellHidesMiniPlayer(BookDetailPlaceholderRoute("Settings")) shouldBe false
            shellHidesMiniPlayer(null) shouldBe false
        }
    }
})
