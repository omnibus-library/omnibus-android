package com.omnibus.omnibus.data.auth.local

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

class StoredAuthTokenProviderTest : DescribeSpec({

    lateinit var tokenStore: FakeAuthTokenStore
    lateinit var provider: StoredAuthTokenProvider

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    beforeEach {
        tokenStore = FakeAuthTokenStore()
        provider = StoredAuthTokenProvider(tokenStore)
    }

    describe("token") {

        it("TC-16: returns the stored token") {
            tokenStore.save(TOKEN)

            provider.token() shouldBe TOKEN
        }

        it("TC-16: returns null when no token is stored") {
            provider.token().shouldBeNull()
        }

        listOf("", "   ").forEach { blank ->
            it("TC-16: returns null when the stored token is \"$blank\"") {
                tokenStore.save(blank)

                provider.token().shouldBeNull()
            }
        }

        it("TC-16: returns null when reading the token fails with an IOException") {
            tokenStore.save(TOKEN)
            tokenStore.readFailure = IOException("corrupt")

            provider.token().shouldBeNull()
        }

        it("TC-16: reflects a token saved after the provider was created") {
            provider.token().shouldBeNull()

            tokenStore.save(TOKEN)

            provider.token() shouldBe TOKEN
        }

        it("TC-16: rethrows cancellation raised while reading") {
            tokenStore.readFailure = CancellationException("cancelled")

            shouldThrow<CancellationException> { provider.token() }
        }
    }

    describe("invalidate") {

        it("TC-17: clears the stored token") {
            tokenStore.save(TOKEN)

            provider.invalidate()

            tokenStore.token.first().shouldBeNull()
            tokenStore.clearCalls shouldBe 1
        }

        it("TC-17: swallows an IOException from clearing the token") {
            tokenStore.save(TOKEN)
            tokenStore.clearFailure = IOException("disk full")

            provider.invalidate()

            tokenStore.clearCalls shouldBe 1
            tokenStore.token.first() shouldBe TOKEN
        }

        it("TC-17: rethrows cancellation raised while clearing") {
            tokenStore.save(TOKEN)
            tokenStore.clearFailure = CancellationException("cancelled")

            shouldThrow<CancellationException> { provider.invalidate() }
        }
    }
}) {
    private companion object {
        const val TOKEN = "abc123"
    }
}
