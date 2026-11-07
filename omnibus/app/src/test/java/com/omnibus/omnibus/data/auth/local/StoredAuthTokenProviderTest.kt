package com.omnibus.omnibus.data.auth.local

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

class StoredAuthTokenProviderTest : DescribeSpec({

    fun sut(tokenStore: AuthTokenStore = TestAuthTokenStore()): StoredAuthTokenProvider =
        StoredAuthTokenProvider(tokenStore)

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading the token") {
        context("when a token is stored") {
            it("TC-16: returns it") {
                val token = randomString()

                sut(TestAuthTokenStore(initialToken = token)).token() shouldBe token
            }
        }

        context("when no token is stored") {
            it("TC-16: returns null") {
                sut().token().shouldBeNull()
            }
        }

        context("when the stored token is blank") {
            it("TC-16: returns null for empty and whitespace tokens") {
                checkAll(Exhaustive.of("", "   ")) { blank ->
                    sut(TestAuthTokenStore(initialToken = blank)).token().shouldBeNull()
                }
            }
        }

        context("when reading fails with an IOException") {
            it("TC-16: returns null") {
                val tokenStore = TestAuthTokenStore(initialToken = randomString()).apply {
                    readFailure = IOException("corrupt")
                }

                sut(tokenStore).token().shouldBeNull()
            }
        }

        context("when reading is cancelled") {
            it("TC-16: rethrows the cancellation") {
                val tokenStore = TestAuthTokenStore().apply { readFailure = CancellationException("cancelled") }

                shouldThrow<CancellationException> { sut(tokenStore).token() }
            }
        }
    }

    describe("invalidating the token") {
        it("TC-17: clears the stored token") {
            val tokenStore = TestAuthTokenStore(initialToken = randomString())

            sut(tokenStore).invalidate()

            tokenStore.token.first().shouldBeNull()
        }

        context("when clearing fails with an IOException") {
            it("TC-17: doesn't throw") {
                val tokenStore = TestAuthTokenStore().apply { clearFailure = IOException("disk full") }

                shouldNotThrowAny { sut(tokenStore).invalidate() }
            }
        }

        context("when clearing is cancelled") {
            it("TC-17: rethrows the cancellation") {
                val tokenStore = TestAuthTokenStore().apply { clearFailure = CancellationException("cancelled") }

                shouldThrow<CancellationException> { sut(tokenStore).invalidate() }
            }
        }
    }
})
