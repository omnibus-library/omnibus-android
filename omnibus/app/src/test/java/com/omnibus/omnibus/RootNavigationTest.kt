package com.omnibus.omnibus

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.auth.ui.LoginScreenRoute
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreenRoute
import com.omnibus.omnibus.feature.library.ui.PlayerStubRoute
import com.omnibus.omnibus.feature.reader.ui.ReaderRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RootNavigationTest : DescribeSpec({

    describe("closeReader") {

        it("TC-75: pops only its own route, and a second close is a no-op") {
            val route = ReaderRoute("a")
            val stack = mutableListOf<NavKey>(SignedInShellRoute, route)

            stack.closeReader(route)
            stack shouldContainExactly listOf(SignedInShellRoute)

            stack.closeReader(route)
            stack shouldContainExactly listOf(SignedInShellRoute)
        }

        it("TC-75: a reader that is not on top is not removed") {
            val route = ReaderRoute("a")
            val stack = mutableListOf<NavKey>(SignedInShellRoute, route, LoginScreenRoute)

            stack.closeReader(route)

            stack shouldContainExactly listOf(SignedInShellRoute, route, LoginScreenRoute)
        }
    }

    describe("openReader") {

        it("TC-75: opening the same book twice pushes it once") {
            val stack = mutableListOf<NavKey>(SignedInShellRoute)

            stack.openReader("a")
            stack.openReader("a")

            stack shouldContainExactly listOf(SignedInShellRoute, ReaderRoute("a"))
        }

        it("#117 TC-57: openReader carries the start cfi, and a different cfi is pushed") {
            val stack = mutableListOf<NavKey>(SignedInShellRoute)

            stack.openReader("a", "epubcfi(/6/4)")
            stack.openReader("a", "epubcfi(/6/4)")
            stack.openReader("a", "epubcfi(/6/8)")

            stack shouldContainExactly listOf(
                SignedInShellRoute,
                ReaderRoute("a", "epubcfi(/6/4)"),
                ReaderRoute("a", "epubcfi(/6/8)"),
            )
        }
    }

    describe("route serialization") {

        it("#117 TC-58: routes round trip and decode old payloads") {
            val route = ReaderRoute("a", "epubcfi(/6/4)")
            Json.decodeFromString<ReaderRoute>(Json.encodeToString(route)) shouldBe route
            Json.decodeFromString<ReaderRoute>(Json.encodeToString(ReaderRoute("a"))) shouldBe ReaderRoute("a", null)
            Json.decodeFromString<ReaderRoute>("{\"bookUuid\":\"a\"}") shouldBe ReaderRoute("a", null)

            val player = PlayerStubRoute("a", 9L, 4332.5)
            Json.decodeFromString<PlayerStubRoute>(Json.encodeToString(player)) shouldBe player
            Json.decodeFromString<PlayerStubRoute>("{\"bookUuid\":\"a\",\"bookFileId\":9}") shouldBe
                PlayerStubRoute("a", 9L, null)
        }
    }

    describe("shouldLeaveForLogin") {

        it("TC-83: leaves for Login only when signed out with the shell on the stack") {
            shouldLeaveForLogin(listOf(SignedInShellRoute), isSignedIn = false) shouldBe true
            shouldLeaveForLogin(listOf(SignedInShellRoute, ReaderRoute("a")), isSignedIn = false) shouldBe true
            shouldLeaveForLogin(listOf(SignedInShellRoute), isSignedIn = true) shouldBe false
            shouldLeaveForLogin(listOf(SignedInShellRoute), isSignedIn = null) shouldBe false
            shouldLeaveForLogin(listOf(LoginScreenRoute), isSignedIn = false) shouldBe false
            shouldLeaveForLogin(listOf(ServerConfigurationScreenRoute), isSignedIn = false) shouldBe false
        }

        it("TC-83: leaveForLogin replaces a stack with the reader on top with Login") {
            val stack = mutableListOf<NavKey>(SignedInShellRoute, ReaderRoute("a"))

            stack.leaveForLogin()

            stack shouldContainExactly listOf(LoginScreenRoute)
        }
    }

    describe("drawsOwnInsets") {

        it("TC-85: the shell and the reader draw their own insets; other routes do not") {
            drawsOwnInsets(SignedInShellRoute) shouldBe true
            drawsOwnInsets(ReaderRoute("x")) shouldBe true
            drawsOwnInsets(LoginScreenRoute) shouldBe false
            drawsOwnInsets(ServerConfigurationScreenRoute) shouldBe false
            drawsOwnInsets(null) shouldBe false
        }
    }
})
