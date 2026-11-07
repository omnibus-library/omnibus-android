package com.omnibus.omnibus

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.audiobook.player.PlayerRoute
import com.omnibus.omnibus.feature.auth.ui.LoginScreenRoute
import com.omnibus.omnibus.feature.configuration.ui.ServerConfigurationScreenRoute
import com.omnibus.omnibus.feature.reader.ui.ReaderRoute
import com.omnibus.omnibus.feature.shell.ui.SignedInShellRoute
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RootNavigationTest : DescribeSpec({

    describe("closing the reader") {

        it("TC-75: pops its own route a single time, for repeated closes") {
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

    describe("opening the player") {

        it("#165 TC-1: ignores an identical PlayerRoute already on top") {
            val stack = mutableListOf<NavKey>(SignedInShellRoute)

            stack.openPlayer("a", 9L, 120.0)
            stack.openPlayer("a", 9L, 120.0)

            stack shouldContainExactly listOf(SignedInShellRoute, PlayerRoute("a", 9L, 120.0))
        }

        it("#165 TC-2: pushes a route that differs in position or file") {
            val stack = mutableListOf<NavKey>(SignedInShellRoute)

            stack.openPlayer("a", 9L, 120.0)
            stack.openPlayer("a", 9L, 240.0)
            stack.openPlayer("a", 10L, 240.0)

            stack shouldContainExactly listOf(
                SignedInShellRoute,
                PlayerRoute("a", 9L, 120.0),
                PlayerRoute("a", 9L, 240.0),
                PlayerRoute("a", 10L, 240.0),
            )
        }
    }

    describe("closing the player") {

        it("#165 TC-3: pops its own route only from the top, a single time") {
            val route = PlayerRoute("a", 9L)
            val stack = mutableListOf<NavKey>(SignedInShellRoute, route)

            stack.closePlayer(route)
            stack shouldContainExactly listOf(SignedInShellRoute)

            stack.closePlayer(route)
            stack shouldContainExactly listOf(SignedInShellRoute)

            val buried = mutableListOf<NavKey>(SignedInShellRoute, route, LoginScreenRoute)
            buried.closePlayer(route)
            buried shouldContainExactly listOf(SignedInShellRoute, route, LoginScreenRoute)
        }
    }

    describe("opening the reader") {

        it("TC-75: pushes the same book once") {
            val stack = mutableListOf<NavKey>(SignedInShellRoute)

            stack.openReader("a")
            stack.openReader("a")

            stack shouldContainExactly listOf(SignedInShellRoute, ReaderRoute("a"))
        }

        it("#117 TC-57: pushes one route per start cfi") {
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

        it("#117 TC-58: decodes current and old payloads") {
            val route = ReaderRoute("a", "epubcfi(/6/4)")
            Json.decodeFromString<ReaderRoute>(Json.encodeToString(route)) shouldBe route
            Json.decodeFromString<ReaderRoute>(Json.encodeToString(ReaderRoute("a"))) shouldBe ReaderRoute("a", null)
            Json.decodeFromString<ReaderRoute>("{\"bookUuid\":\"a\"}") shouldBe ReaderRoute("a", null)

            // #165 TC-5: the stub's payloads decode into PlayerRoute.
            val player = PlayerRoute("a", 9L, 4332.5)
            Json.decodeFromString<PlayerRoute>(Json.encodeToString(player)) shouldBe player
            Json.decodeFromString<PlayerRoute>("{\"bookUuid\":\"a\",\"bookFileId\":9}") shouldBe
                PlayerRoute("a", 9L, null)
        }
    }

    describe("leaving for login") {

        it("TC-83: leaves only for a signed-out user with the shell on the stack") {
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

    describe("which routes draw their own insets") {

        it("TC-85: includes the shell and the reader, not login, server configuration or no route") {
            drawsOwnInsets(SignedInShellRoute) shouldBe true
            drawsOwnInsets(ReaderRoute("x")) shouldBe true
            drawsOwnInsets(LoginScreenRoute) shouldBe false
            drawsOwnInsets(ServerConfigurationScreenRoute) shouldBe false
            drawsOwnInsets(null) shouldBe false
        }

        it("#165 TC-4: is true for PlayerRoute") {
            drawsOwnInsets(PlayerRoute("x", null)) shouldBe true
        }
    }
})
