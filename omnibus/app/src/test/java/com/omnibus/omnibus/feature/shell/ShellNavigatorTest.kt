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
