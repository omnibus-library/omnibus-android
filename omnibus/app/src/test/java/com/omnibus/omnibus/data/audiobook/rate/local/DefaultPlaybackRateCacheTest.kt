package com.omnibus.omnibus.data.audiobook.rate.local

import com.omnibus.omnibus.data.audiobook.TempDataStore
import com.omnibus.omnibus.data.audiobook.unreadableDataStore
import com.omnibus.omnibus.data.audiobook.unwritableDataStore
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DefaultPlaybackRateCacheTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("saving a local rate") {

        // R14, I-SPD-3: the newest wins per book, and a rate known only from the server is not pending
        it("#168 TC-26: leaves the newest rate of each saved book pending (I-SPD-3)") {
            TempDataStore().use { temp ->
                val cache = DefaultPlaybackRateCache(temp.dataStore)
                cache.saveLocal("a", 1.25)
                cache.saveLocal("b", 1.5)
                cache.storeServer("c", 2.0)

                cache.saveLocal("a", 1.75)

                cache.allPending() shouldBe mapOf("a" to 1.75, "b" to 1.5)
            }
        }
    }

    describe("storing the server's rate") {

        // R14: it never touches a pending rate
        it("#168 TC-27: leaves a pending rate alone (R14)") {
            TempDataStore().use { temp ->
                val cache = DefaultPlaybackRateCache(temp.dataStore)
                val book = randomString()
                cache.saveLocal(book, 1.25)

                cache.storeServer(book, 2.0)

                cache.pending(book) shouldBe 1.25
            }
        }

        context("when the server has no rate") {
            // R14
            it("#168 TC-27: removes the cached rate (R14)") {
                TempDataStore().use { temp ->
                    val cache = DefaultPlaybackRateCache(temp.dataStore)
                    val book = randomString()
                    cache.storeServer(book, randomDouble())

                    cache.storeServer(book, null)

                    cache.cached(book).shouldBeNull()
                }
            }
        }
    }

    describe("clearing a sent rate") {

        context("when it is still the pending rate") {
            // I-SPD-3
            it("#168 TC-28: clears it (I-SPD-3)") {
                TempDataStore().use { temp ->
                    val cache = DefaultPlaybackRateCache(temp.dataStore)
                    val book = randomString()
                    val rate = randomDouble()
                    cache.saveLocal(book, rate)

                    cache.clearPendingIf(book, rate)

                    cache.pending(book).shouldBeNull()
                }
            }
        }

        context("when a newer rate is pending") {
            // I-SPD-3
            it("#168 TC-28: keeps the newer rate (I-SPD-3)") {
                TempDataStore().use { temp ->
                    val cache = DefaultPlaybackRateCache(temp.dataStore)
                    val book = randomString()
                    cache.saveLocal(book, 1.5)

                    cache.clearPendingIf(book, 1.25)

                    cache.pending(book) shouldBe 1.5
                }
            }
        }
    }

    describe("surviving failures") {

        // R14
        it("#168 TC-29: keeps the rates across a restart (R14)") {
            TempDataStore().use { temp ->
                val book = randomString()
                val rate = randomDouble()
                DefaultPlaybackRateCache(temp.dataStore).saveLocal(book, rate)

                val reopened = DefaultPlaybackRateCache(temp.reopen())

                reopened.cached(book) to reopened.pending(book) shouldBe (rate to rate)
            }
        }

        context("when the file can't be read") {
            // R14
            it("#168 TC-30: reads as empty (R14, R16)") {
                val cache = DefaultPlaybackRateCache(unreadableDataStore())

                cache.cached(randomString()).shouldBeNull()
                cache.pending(randomString()).shouldBeNull()
                cache.allPending().shouldBeEmpty()
            }
        }

        context("when the file can't be written") {
            // R14, I-SPD-1
            it("#168 TC-30: throws nothing (R14, R16)") {
                val cache = DefaultPlaybackRateCache(unwritableDataStore())
                val book = randomString()

                shouldNotThrowAny {
                    cache.saveLocal(book, randomDouble())
                    cache.storeServer(book, randomDouble())
                    cache.clearPendingIf(book, randomDouble())
                }
            }
        }
    }
})
