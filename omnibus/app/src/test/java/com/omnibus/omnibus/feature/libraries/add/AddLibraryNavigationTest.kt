package com.omnibus.omnibus.feature.libraries.add

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.feature.libraries.switching.SwitchingRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AddLibraryNavigationTest : DescribeSpec({

    fun sut(): MutableList<NavKey> = mutableListOf(SignedInShellRoute)

    describe("the add flow's stack") {
        it("TC-11: opens Connect once over the shell, and sign-in over Connect once") {
            val stack = sut()
            val address = randomString()

            stack.openAddLibrary()
            stack.openAddLibrary()
            stack.openAddLibrarySignIn(address)
            stack.openAddLibrarySignIn(address)

            stack shouldBe listOf(SignedInShellRoute, AddLibraryConnectRoute, AddLibrarySignInRoute(address))
        }

        it("TC-11: going back from sign-in returns to Connect") {
            val stack = sut()
            val address = randomString()
            stack.openAddLibrary()
            stack.openAddLibrarySignIn(address)

            stack.closeAddLibrarySignIn(AddLibrarySignInRoute(address))

            stack shouldBe listOf(SignedInShellRoute, AddLibraryConnectRoute)
        }

        it("TC-11: a back from sign-in for a route that isn't on top changes nothing") {
            val stack = sut()
            val address = randomString()
            stack.openAddLibrary()
            stack.openAddLibrarySignIn(address)

            // Derived from the address on top, so the other route can't collide with it.
            stack.closeAddLibrarySignIn(AddLibrarySignInRoute("$address-other"))

            stack shouldBe listOf(SignedInShellRoute, AddLibraryConnectRoute, AddLibrarySignInRoute(address))
        }

        it("TC-11: closing the flow removes Connect and sign-in, leaving the shell") {
            val stack = sut()
            stack.openAddLibrary()
            stack.openAddLibrarySignIn(randomString())

            stack.closeAddLibrary()

            stack shouldBe listOf(SignedInShellRoute)
        }

        it("TC-11: closing the flow changes nothing when neither add route is on top") {
            val stack = sut()

            stack.closeAddLibrary()

            stack shouldBe listOf(SignedInShellRoute)
        }

        it("TC-12: a saved sign-in finishes into an add switch, and the flow's routes are removed") {
            val stack = sut()
            val id = randomString()
            stack.openAddLibrary()
            stack.openAddLibrarySignIn(randomString())

            stack.finishAddingLibrary(id)

            stack shouldBe listOf(SignedInShellRoute, SwitchingRoute(id, kind = SwitchKind.Add))
        }

        it("TC-12: switching to an inactive saved library opens a normal switch") {
            val stack = sut()
            val library = randomLibrary().copy(isActive = false)
            stack.openAddLibrary()

            stack.switchFromAddLibrary(library)

            stack shouldBe listOf(SignedInShellRoute, SwitchingRoute(library.id))
        }

        it("TC-12: switching to the active saved library only closes the flow") {
            val stack = sut()
            val library = randomLibrary().copy(isActive = true)
            stack.openAddLibrary()

            stack.switchFromAddLibrary(library)

            stack shouldBe listOf(SignedInShellRoute)
        }
    }
})
