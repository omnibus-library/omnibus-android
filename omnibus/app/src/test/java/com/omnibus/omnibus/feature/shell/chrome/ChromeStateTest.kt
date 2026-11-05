package com.omnibus.omnibus.feature.shell.chrome

import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.shell.LibraryRoot
import com.omnibus.omnibus.feature.shell.SearchRoot
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.feature.shell.StatsRoot
import com.omnibus.omnibus.feature.shell.YouRoot
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ChromeStateTest : DescribeSpec({

    describe("of") {
        // P-FAB-3, P-VIS-1
        it("TC-23: Library root shows the chrome and the FAB") {
            ChromeState.of(ShellTab.Library, listOf(LibraryRoot)) shouldBe ChromeState(visible = true, fabVisible = true)
        }

        // P-VIS-1
        it("#172 TC-13: Book Detail on top hides the chrome") {
            ChromeState.of(ShellTab.Library, listOf(LibraryRoot, BookDetailRoute("u"))) shouldBe
                ChromeState(visible = false, fabVisible = false)
        }

        // I-STATE-1
        it("TC-25: a screen above Book Detail keeps it hidden") {
            val stack = listOf(LibraryRoot, BookDetailRoute("u"), BookDetailPlaceholderRoute("Settings"))

            ChromeState.of(ShellTab.Library, stack).visible shouldBe false
        }

        // P-FAB-3
        it("TC-26: FAB only on Library's root") {
            ChromeState.of(ShellTab.Library, listOf(LibraryRoot, BookDetailPlaceholderRoute("Settings"))) shouldBe
                ChromeState(visible = true, fabVisible = false)
        }

        // P-VIS-1, P-FAB-3
        it("#172 TC-14: the other roots show the chrome without the FAB") {
            ChromeState.of(ShellTab.Search, listOf(SearchRoot)) shouldBe ChromeState(visible = true, fabVisible = false)
            ChromeState.of(ShellTab.Stats, listOf(StatsRoot)) shouldBe ChromeState(visible = true, fabVisible = false)
            ChromeState.of(ShellTab.You, listOf(YouRoot)) shouldBe ChromeState(visible = true, fabVisible = false)
            ChromeState.of(ShellTab.Library, emptyList()) shouldBe ChromeState(visible = true, fabVisible = false)
        }
    }
})
