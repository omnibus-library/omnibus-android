package com.omnibus.omnibus.feature.shell

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomOf
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private data class Pushed(val id: Int) : NavKey

private fun randomPushed() = Pushed(randomInt())

private fun rootStacks(): Map<ShellTab, MutableList<NavKey>> = ShellTab.entries.associateWith { mutableStateListOf(it.root) }

private fun tabOtherThan(tab: ShellTab): ShellTab = randomOf(ShellTab.entries - tab)

class ShellNavigatorTest : DescribeSpec({

    fun sut(
        stacks: Map<ShellTab, MutableList<NavKey>> = rootStacks(),
        selected: ShellTab = randomEnum(),
    ) = ShellNavigator(stacks, mutableStateOf(selected))

    describe("selecting a tab") {
        it("switches to the tapped tab") {
            val tab = randomEnum<ShellTab>()
            val navigator = sut(selected = tabOtherThan(tab))

            navigator.select(tab)

            navigator.selected shouldBe tab
        }

        it("keeps each tab's own history across a switch") {
            val stacks = rootStacks()
            val first = randomEnum<ShellTab>()
            val second = tabOtherThan(first)
            val pushed = randomPushed()
            val navigator = sut(stacks, first)
            navigator.navigateTo(pushed)

            navigator.select(second)
            navigator.navigateTo(randomPushed())
            navigator.select(first)

            stacks.getValue(first).toList() shouldBe listOf(first.root, pushed)
        }

        context("when the tab is already selected") {
            it("pops it to its root") {
                val stacks = rootStacks()
                val tab = randomEnum<ShellTab>()
                val navigator = sut(stacks, tab)
                navigator.navigateTo(randomPushed())
                navigator.navigateTo(randomPushed())

                navigator.select(tab)

                stacks.getValue(tab).toList() shouldBe listOf(tab.root)
            }
        }
    }

    describe("pushing a destination") {
        it("ignores a destination already on top, so a double tap stacks nothing") {
            val stacks = rootStacks()
            val tab = randomEnum<ShellTab>()
            val pushed = randomPushed()
            val navigator = sut(stacks, tab)

            navigator.navigateTo(pushed)
            navigator.navigateTo(pushed)

            stacks.getValue(tab).toList() shouldBe listOf(tab.root, pushed)
        }

        it("lands on the tab that asked, whichever tab is selected by then") {
            val stacks = rootStacks()
            val asking = randomEnum<ShellTab>()
            val selected = tabOtherThan(asking)
            val pushed = randomPushed()
            val navigator = sut(stacks, selected)

            navigator.navigateTo(pushed, asking)

            listOf(stacks.getValue(asking).toList(), stacks.getValue(selected).toList()) shouldBe
                listOf(listOf(asking.root, pushed), listOf(selected.root))
        }
    }

    describe("opening a destination that may already be underneath") {
        it("goes back to it when it is just below the top") {
            val stacks = rootStacks()
            val tab = randomEnum<ShellTab>()
            val below = randomPushed()
            val navigator = sut(stacks, tab)
            navigator.navigateTo(below)
            navigator.navigateTo(Pushed(below.id + 1))

            navigator.openOrPop(below, tab)

            stacks.getValue(tab).toList() shouldBe listOf(tab.root, below)
        }

        it("goes back on the tab that asked, whichever tab is selected by then") {
            val stacks = rootStacks()
            val asking = randomEnum<ShellTab>()
            val below = randomPushed()
            val navigator = sut(stacks, tabOtherThan(asking))
            navigator.navigateTo(below, asking)
            navigator.navigateTo(Pushed(below.id + 1), asking)

            navigator.openOrPop(below, asking)

            stacks.getValue(asking).toList() shouldBe listOf(asking.root, below)
        }

        it("pushes it when it is not just below the top") {
            val stacks = rootStacks()
            val tab = randomEnum<ShellTab>()
            val top = randomPushed()
            val wanted = Pushed(top.id + 1)
            val navigator = sut(stacks, tab)
            navigator.navigateTo(top)

            navigator.openOrPop(wanted, tab)

            stacks.getValue(tab).toList() shouldBe listOf(tab.root, top, wanted)
        }
    }

    describe("finding the tab a destination belongs to") {
        it("is the selected tab when both tabs have it on top") {
            val stacks = rootStacks()
            val selected = randomEnum<ShellTab>()
            val other = tabOtherThan(selected)
            val shared = randomPushed()
            val navigator = sut(stacks, selected)
            navigator.navigateTo(shared, selected)
            navigator.navigateTo(shared, other)

            navigator.ownerOf(shared) shouldBe selected
        }

        it("is the other tab when only it has the destination on top, as it fades out") {
            val stacks = rootStacks()
            val selected = randomEnum<ShellTab>()
            val other = tabOtherThan(selected)
            val pushed = randomPushed()
            val navigator = sut(stacks, selected)
            navigator.navigateTo(pushed, other)

            navigator.ownerOf(pushed) shouldBe other
        }

        it("is the selected tab when no tab has the destination on top") {
            val selected = randomEnum<ShellTab>()

            sut(selected = selected).ownerOf(randomPushed()) shouldBe selected
        }
    }

    describe("the flattened back stack") {
        it("ends with the selected tab's stack") {
            val tab = randomEnum<ShellTab>()
            val pushed = randomPushed()
            val navigator = sut(selected = tab)

            navigator.navigateTo(pushed)

            navigator.backStack.takeLast(2) shouldBe listOf(tab.root, pushed)
        }

        it("contains every tab's entries so their state is kept") {
            val pushed = randomPushed()
            val navigator = sut()
            navigator.navigateTo(pushed)

            navigator.select(tabOtherThan(navigator.selected))

            navigator.backStack.toSet() shouldBe ShellTab.entries.map { it.root }.toSet() + pushed
        }
    }

    describe("going back") {
        context("when the selected tab has history") {
            it("pops the top destination") {
                val stacks = rootStacks()
                val tab = randomEnum<ShellTab>()
                val navigator = sut(stacks, tab)
                navigator.navigateTo(randomPushed())

                navigator.back() shouldBe true

                stacks.getValue(tab).toList() shouldBe listOf(tab.root)
            }
        }

        context("when the asking tab is not the selected one") {
            it("pops that tab's top destination") {
                val stacks = rootStacks()
                val asking = randomEnum<ShellTab>()
                val navigator = sut(stacks, tabOtherThan(asking))
                navigator.navigateTo(randomPushed(), asking)

                navigator.back(asking) shouldBe true

                stacks.getValue(asking).toList() shouldBe listOf(asking.root)
            }
        }

        context("when at the root of another tab") {
            it("returns to the start tab") {
                val navigator = sut(selected = tabOtherThan(ShellTab.Start))

                navigator.back() shouldBe true

                navigator.selected shouldBe ShellTab.Start
            }
        }

        context("when at the start tab's root") {
            it("reports nowhere to go") {
                sut(selected = ShellTab.Start).back() shouldBe false
            }
        }
    }
})
