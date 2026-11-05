package com.omnibus.omnibus.feature.shell

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import androidx.navigation3.runtime.NavKey
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private data class Pushed(val id: Int) : NavKey

class ShellNavigatorTest : DescribeSpec({

    lateinit var stacks: Map<ShellTab, MutableList<NavKey>>
    lateinit var navigator: ShellNavigator

    fun stack(tab: ShellTab) = stacks.getValue(tab).toList()

    beforeEach {
        stacks = ShellTab.entries.associateWith { mutableStateListOf(it.root) }
        navigator = ShellNavigator(stacks, mutableStateOf(ShellTab.Start))
    }

    describe("select") {
        it("switches to the tapped tab") {
            navigator.select(ShellTab.Stats)

            navigator.selected shouldBe ShellTab.Stats
        }

        it("keeps each tab's own history across a switch") {
            navigator.navigateTo(Pushed(1))
            navigator.select(ShellTab.Search)
            navigator.navigateTo(Pushed(2))

            navigator.select(ShellTab.Library)

            stack(ShellTab.Library) shouldBe listOf(LibraryRoot, Pushed(1))
            stack(ShellTab.Search) shouldBe listOf(SearchRoot, Pushed(2))
        }

        it("pops the selected tab to its root when it is tapped again") {
            navigator.navigateTo(Pushed(1))
            navigator.navigateTo(Pushed(2))

            navigator.select(ShellTab.Library)

            stack(ShellTab.Library) shouldBe listOf(LibraryRoot)
            navigator.selected shouldBe ShellTab.Library
        }

        it("leaves other tabs' history alone when popping to root") {
            navigator.select(ShellTab.Stats)
            navigator.navigateTo(Pushed(1))
            navigator.select(ShellTab.Library)
            navigator.navigateTo(Pushed(2))

            navigator.select(ShellTab.Library)

            stack(ShellTab.Stats) shouldBe listOf(StatsRoot, Pushed(1))
        }

        it("pops Search to its root when reselected") {
            navigator.select(ShellTab.Search)
            navigator.navigateTo(Pushed(1))

            navigator.select(ShellTab.Search)

            stack(ShellTab.Search) shouldBe listOf(SearchRoot)
            navigator.selected shouldBe ShellTab.Search
        }
    }

    describe("tabs") {
        it("TC-28: are Library, Search, Stats and You, with Library as the start tab") {
            ShellTab.entries shouldBe listOf(ShellTab.Library, ShellTab.Search, ShellTab.Stats, ShellTab.You)
            ShellTab.Start shouldBe ShellTab.Library
            ShellTab.Search.root shouldBe SearchRoot
        }
    }

    describe("backStack") {
        it("ends with the selected tab's stack") {
            navigator.select(ShellTab.Stats)
            navigator.navigateTo(Pushed(1))

            navigator.backStack.takeLast(2) shouldBe listOf(StatsRoot, Pushed(1))
        }

        it("still contains every tab's entries so their state is kept") {
            navigator.navigateTo(Pushed(1))
            navigator.select(ShellTab.You)

            navigator.backStack.toSet() shouldBe ShellTab.entries.map { it.root }.toSet() + Pushed(1)
        }
    }

    describe("back") {
        it("pops the selected tab's top destination") {
            navigator.navigateTo(Pushed(1))

            navigator.back() shouldBe true

            stack(ShellTab.Library) shouldBe listOf(LibraryRoot)
        }

        it("returns to Library from the root of another tab") {
            navigator.select(ShellTab.You)

            navigator.back() shouldBe true

            navigator.selected shouldBe ShellTab.Library
        }

        it("returns to Library from every other root") {
            listOf(ShellTab.Search, ShellTab.Stats, ShellTab.You).forEach { tab ->
                navigator.select(tab)

                navigator.back() shouldBe true

                navigator.selected shouldBe ShellTab.Library
            }
        }

        it("stays on the other tab while it has history") {
            navigator.select(ShellTab.You)
            navigator.navigateTo(Pushed(1))

            navigator.back()

            navigator.selected shouldBe ShellTab.You
        }

        it("reports nowhere to go at Library's root") {
            navigator.back() shouldBe false
        }
    }

    describe("book detail") {
        it("TC-75: pushes onto the Library stack only, and back pops to the Library root") {
            navigator.navigateTo(BookDetailRoute("abc"))

            stack(ShellTab.Library) shouldBe listOf(LibraryRoot, BookDetailRoute("abc"))
            ShellTab.entries.filter { it != ShellTab.Library }.forEach { tab ->
                stack(tab) shouldBe listOf(tab.root)
            }

            navigator.back() shouldBe true

            stack(ShellTab.Library) shouldBe listOf(LibraryRoot)
        }
    }

    describe("construction") {
        it("rejects a stack that doesn't start at its tab's root") {
            val broken = stacks + (ShellTab.Stats to mutableStateListOf<NavKey>(Pushed(1)))

            runCatching { ShellNavigator(broken, mutableStateOf(ShellTab.Start)) }.isFailure shouldBe true
        }
    }
})
