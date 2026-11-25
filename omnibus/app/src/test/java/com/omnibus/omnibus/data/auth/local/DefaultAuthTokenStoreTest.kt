package com.omnibus.omnibus.data.auth.local

import app.cash.turbine.test
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.local.TestLibraryTokenStore
import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class DefaultAuthTokenStoreTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("observing the token") {
        it("TC-31: shows the active library's token, then the new library's") {
            val active = randomLibrary().copy(isActive = true)
            val token = randomString()
            val libraries = TestLibrariesRepository(listOf(active))
            val store = DefaultAuthTokenStore(TestLibraryTokenStore(mapOf(active.id to token)), libraries)

            store.token.test {
                awaitItem() shouldBe token
                libraries.add("https://${randomString().lowercase()}.example", activate = true)
                awaitItem().shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    describe("saving a token") {
        it("TC-32: saves the token for the active library only") {
            val active = randomLibrary().copy(isActive = true)
            val other = randomLibrary().copy(isActive = false)
            val otherToken = randomString()
            val tokens = TestLibraryTokenStore(mapOf(other.id to otherToken))
            val store = DefaultAuthTokenStore(tokens, TestLibrariesRepository(listOf(active, other)))
            val token = randomString()

            store.save(token)

            tokens.getTokenFlow(active.id).first() shouldBe token
            tokens.getTokenFlow(other.id).first() shouldBe otherToken
        }

        context("when no library is active") {
            it("TC-33: stores no token for any library") {
                val inactive = randomLibrary().copy(isActive = false)
                val tokens = TestLibraryTokenStore()
                val store = DefaultAuthTokenStore(tokens, TestLibrariesRepository(listOf(inactive)))

                shouldNotThrowAny { store.save(randomString()) }

                tokens.getTokenFlow(inactive.id).first().shouldBeNull()
            }
        }
    }

    describe("clearing a token") {
        it("TC-32: clears the active library's token only") {
            val active = randomLibrary().copy(isActive = true)
            val other = randomLibrary().copy(isActive = false)
            val otherToken = randomString()
            val tokens = TestLibraryTokenStore(mapOf(active.id to randomString(), other.id to otherToken))
            val store = DefaultAuthTokenStore(tokens, TestLibrariesRepository(listOf(active, other)))

            store.clear()

            tokens.getTokenFlow(active.id).first().shouldBeNull()
            tokens.getTokenFlow(other.id).first() shouldBe otherToken
        }

        context("when no library is active") {
            it("TC-33: ignores the clear") {
                val inactive = randomLibrary().copy(isActive = false)
                val store = DefaultAuthTokenStore(TestLibraryTokenStore(), TestLibrariesRepository(listOf(inactive)))

                shouldNotThrowAny { store.clear() }
            }
        }
    }
})
