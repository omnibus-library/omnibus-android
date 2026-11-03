package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.local.DefaultExportFileStore
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files

class DefaultExportFileStoreTest : DescribeSpec({

    val now = 1_800_000_000_000L
    val hour = 60L * 60 * 1000
    lateinit var tempDir: File

    beforeEach { tempDir = Files.createTempDirectory("export-store").toFile() }

    afterEach { tempDir.deleteRecursively() }

    describe("DefaultExportFileStore") {

        it("#118 TC-56: creates a missing root, returns two distinct empty folders under it, and reports usable space") {
            val root = File(tempDir, "book-exports")
            val store = DefaultExportFileStore(root = root, nowMillis = { now })

            val first = store.newFolder()
            val second = store.newFolder()

            root.isDirectory shouldBe true
            listOf(first, second).forEach { folder ->
                folder.isDirectory shouldBe true
                folder.parentFile shouldBe root
                folder.listFiles().orEmpty().size shouldBe 0
            }
            first shouldNotBe second
            (store.usableBytes() >= 0) shouldBe true
        }

        it("#118 TC-57: newFolder removes folders and stray files older than 24 h, keeps newer ones, and returns a new folder") {
            val root = File(tempDir, "book-exports").apply { mkdirs() }
            // Contents are written before the timestamp is set, since writing a child changes a folder's mtime.
            val old = File(root, "old").apply {
                mkdirs()
                File(this, "a.epub").writeText("a")
            }
            old.setLastModified(now - 25 * hour)
            val recent = File(root, "recent").apply {
                mkdirs()
                File(this, "b.epub").writeText("b")
            }
            recent.setLastModified(now - 23 * hour)
            val stray = File(root, "stray.txt").apply { writeText("c") }
            stray.setLastModified(now - 25 * hour)

            val created = DefaultExportFileStore(root = root, nowMillis = { now }).newFolder()

            old.exists() shouldBe false
            recent.exists() shouldBe true
            stray.exists() shouldBe false
            created.isDirectory shouldBe true
            created.parentFile shouldBe root
        }
    }
})
