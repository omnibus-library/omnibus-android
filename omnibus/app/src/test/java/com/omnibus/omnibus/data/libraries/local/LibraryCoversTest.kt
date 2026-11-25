package com.omnibus.omnibus.data.libraries.local

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

private inline fun <T> withFilesDir(block: (File) -> T): T {
    val filesDir = Files.createTempDirectory("files").toFile()
    return try {
        block(filesDir)
    } finally {
        filesDir.deleteRecursively()
    }
}

class LibraryCoversTest : DescribeSpec({

    describe("moving the loose cover files into a library") {
        it("TC-45: moves loose files and skips library directories") {
            withFilesDir { filesDir ->
                val covers = LibraryCovers(filesDir)
                val libraryId = randomString()
                val otherLibrary = "other-${randomString()}"
                covers.root.mkdirs()
                File(covers.root, "journal").writeText("journal")
                File(covers.root, "entry-1").writeText("cover-1")
                covers.directory(otherLibrary).mkdirs()
                File(covers.directory(otherLibrary), "entry-2").writeText("cover-2")

                covers.moveLooseFilesInto(libraryId)

                File(covers.directory(libraryId), "journal").readText() shouldBe "journal"
                File(covers.directory(libraryId), "entry-1").readText() shouldBe "cover-1"
                covers.root.listFiles()!!.filter { it.isFile }.shouldBeEmpty()
                File(covers.directory(otherLibrary), "entry-2").readText() shouldBe "cover-2"
            }
        }

        it("TC-45: a second move changes nothing") {
            withFilesDir { filesDir ->
                val covers = LibraryCovers(filesDir)
                val libraryId = randomString()
                covers.root.mkdirs()
                File(covers.root, "entry-1").writeText("cover-1")
                covers.moveLooseFilesInto(libraryId)

                covers.moveLooseFilesInto(libraryId)

                File(covers.directory(libraryId), "entry-1").readText() shouldBe "cover-1"
                covers.root.listFiles()!!.filter { it.isFile }.shouldBeEmpty()
            }
        }
    }
})
