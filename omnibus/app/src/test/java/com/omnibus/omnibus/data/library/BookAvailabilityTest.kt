package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import java.io.File
import kotlinx.coroutines.Dispatchers

private fun filesOnDisk(found: Boolean) = LocalBookFiles { if (found) File(randomString()) else null }

class BookAvailabilityTest : DescribeSpec({

    fun sut(
        localBookFiles: LocalBookFiles = filesOnDisk(randomBoolean()),
        connectivity: ConnectivityMonitor = TestConnectivityMonitor(randomBoolean()),
    ) = DefaultBookAvailability(Dispatchers.Unconfined, localBookFiles, connectivity)

    describe("deciding whether a book can open") {

        context("when reading") {
            context("when the file is on disk") {
                it("#22 TC-17: opens, whatever the connectivity") {
                    checkAll(Exhaustive.boolean()) { online ->
                        sut(filesOnDisk(true), TestConnectivityMonitor(online))
                            .canOpen(randomString(), ProgressFormat.Reading) shouldBe true
                    }
                }
            }

            context("when the file is not on disk") {
                it("#22 TC-17: follows the connection") {
                    checkAll(Exhaustive.boolean()) { online ->
                        sut(filesOnDisk(false), TestConnectivityMonitor(online))
                            .canOpen(randomString(), ProgressFormat.Reading) shouldBe online
                    }
                }
            }
        }

        context("when listening") {
            it("#22 TC-17: follows the connection, whether or not a file is on disk") {
                checkAll(Exhaustive.boolean(), Exhaustive.boolean()) { online, fileOnDisk ->
                    sut(filesOnDisk(fileOnDisk), TestConnectivityMonitor(online))
                        .canOpen(randomString(), ProgressFormat.Listening) shouldBe online
                }
            }
        }

        context("when the connectivity changes between calls") {
            it("#22 TC-17: answers from the current connectivity") {
                val monitor = TestConnectivityMonitor(online = true)
                val availability = sut(filesOnDisk(false), monitor)
                availability.canOpen(randomString(), randomEnum<ProgressFormat>())

                monitor.isOnline.value = false

                availability.canOpen(randomString(), randomEnum<ProgressFormat>()) shouldBe false
            }
        }
    }
})
