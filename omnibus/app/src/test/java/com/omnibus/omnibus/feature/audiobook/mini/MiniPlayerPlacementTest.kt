package com.omnibus.omnibus.feature.audiobook.mini

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class MiniPlayerPlacementTest : DescribeSpec({

    describe("hiding the bar in the shell") {
        // P-MINI-1
        it("#172 TC-13: hides it under Book Detail") {
            shellHidesMiniPlayer(BookDetailRoute(randomString())) shouldBe true
        }

        // P-MINI-1
        it("#172 TC-14: shows it over every tab root, the Book Detail placeholder or an empty stack") {
            val tops: List<NavKey?> = ShellTab.entries.map { it.root } + BookDetailPlaceholderRoute(randomString()) + null

            checkAll(Exhaustive.collection(tops)) { top ->
                shellHidesMiniPlayer(top) shouldBe false
            }
        }
    }
})
