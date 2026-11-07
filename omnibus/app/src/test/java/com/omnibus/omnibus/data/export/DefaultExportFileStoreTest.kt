package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.local.DefaultExportFileStore
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files

private const val HOUR = 60L * 60 * 1000

private inline fun <T> withRoot(block: (File) -> T): T {
    val directory = Files.createTempDirectory("export-store").toFile()
    return try {
        block(File(directory, "book-exports"))
    } finally {
        directory.deleteRecursively()
    }
}

/** A folder holding one file, last modified at [modifiedAt]. Written first, since writing a child changes a folder's mtime. */
private fun File.folderModifiedAt(name: String, modifiedAt: Long): File = File(this, name).apply {
    mkdirs()
    File(this, "a.epub").writeText("a")
    setLastModified(modifiedAt)
}

class DefaultExportFileStoreTest : DescribeSpec({

    fun sut(root: File, nowMillis: Long = randomLong()): DefaultExportFileStore =
        DefaultExportFileStore(root = root, nowMillis = { nowMillis })

    describe("handing out a new folder") {
        context("when the root is missing") {
            it("#118 TC-56: creates an empty folder under it") {
                withRoot { root ->
                    val folder = sut(root).newFolder()

                    folder.parentFile shouldBe root
                    folder.listFiles().orEmpty().toList().shouldBeEmpty()
                }
            }
        }

        it("#118 TC-56: gives a different folder each time") {
            withRoot { root ->
                val store = sut(root)

                store.newFolder() shouldNotBe store.newFolder()
            }
        }

        context("when the root holds entries older than 24 hours") {
            it("#118 TC-57: removes old folders") {
                withRoot { root ->
                    root.mkdirs()
                    val now = randomLong(100 * HOUR..1_000_000 * HOUR)
                    val old = root.folderModifiedAt("old", now - 25 * HOUR)

                    sut(root, now).newFolder()

                    old.exists() shouldBe false
                }
            }

            it("#118 TC-57: removes stray files") {
                withRoot { root ->
                    root.mkdirs()
                    val now = randomLong(100 * HOUR..1_000_000 * HOUR)
                    val stray = File(root, "stray.txt").apply {
                        writeText("c")
                        setLastModified(now - 25 * HOUR)
                    }

                    sut(root, now).newFolder()

                    stray.exists() shouldBe false
                }
            }
        }

        context("when the root holds entries newer than 24 hours") {
            it("#118 TC-57: keeps them") {
                withRoot { root ->
                    root.mkdirs()
                    val now = randomLong(100 * HOUR..1_000_000 * HOUR)
                    val recent = root.folderModifiedAt("recent", now - 23 * HOUR)

                    sut(root, now).newFolder()

                    recent.exists() shouldBe true
                }
            }
        }
    }
})
