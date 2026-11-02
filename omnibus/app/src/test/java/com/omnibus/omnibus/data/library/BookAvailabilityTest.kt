package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BookAvailabilityTest : DescribeSpec({

    describe("canOpen") {
        // (medium, a reading file is on disk, the device is online, expected)
        val rows = listOf(
            Quad(ProgressFormat.Reading, true, false, true),
            Quad(ProgressFormat.Reading, false, true, true),
            Quad(ProgressFormat.Reading, false, false, false),
            Quad(ProgressFormat.Listening, true, false, false),
            Quad(ProgressFormat.Listening, false, true, true),
        )
        rows.forEach { row ->
            it("#22 TC-17: ${row.medium} with file ${row.fileOnDisk} and online ${row.online} is ${row.expected}") {
                runTest {
                    val availability = DefaultBookAvailability(
                        UnconfinedTestDispatcher(testScheduler),
                        fakeFiles(row.fileOnDisk),
                        FakeConnectivityMonitor(row.online),
                    )

                    availability.canOpen("b1", row.medium) shouldBe row.expected
                }
            }
        }

        it("#22 TC-17: reads the connectivity at call time") {
            runTest {
                val monitor = FakeConnectivityMonitor(online = true)
                val availability = DefaultBookAvailability(UnconfinedTestDispatcher(testScheduler), fakeFiles(false), monitor)
                availability.canOpen("b1", ProgressFormat.Reading) shouldBe true

                monitor.isOnline.value = false

                availability.canOpen("b1", ProgressFormat.Reading) shouldBe false
            }
        }
    }
})

private data class Quad(
    val medium: ProgressFormat,
    val fileOnDisk: Boolean,
    val online: Boolean,
    val expected: Boolean,
)

private fun fakeFiles(found: Boolean) = LocalBookFiles { if (found) File("x.epub") else null }
