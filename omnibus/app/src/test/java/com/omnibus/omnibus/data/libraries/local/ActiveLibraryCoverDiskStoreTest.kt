package com.omnibus.omnibus.data.libraries.local

import com.omnibus.omnibus.data.cover.local.CachedCover
import com.omnibus.omnibus.data.libraries.TestActiveLibrary
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import okio.ByteString.Companion.encodeUtf8

private inline fun <T> withFilesDir(block: (File) -> T): T {
    val filesDir = Files.createTempDirectory("files").toFile()
    return try {
        block(filesDir)
    } finally {
        filesDir.deleteRecursively()
    }
}

private fun randomCover() = CachedCover(
    bytes = randomString().encodeUtf8(),
    etag = null,
    storedAtMillis = randomLong(),
)

class ActiveLibraryCoverDiskStoreTest : DescribeSpec({

    describe("writing and reading covers") {
        it("TC-41: caches covers under the active library's directory") {
            withFilesDir { filesDir ->
                val library = randomString()
                val covers = LibraryCovers(filesDir)
                val store = ActiveLibraryCoverDiskStore(TestActiveLibrary(library), covers)
                val key = randomString()
                val cover = randomCover()

                store.write(key, cover)

                store.read(key) shouldBe cover
                covers.directory(library).walkTopDown().any { it.isFile } shouldBe true
            }
        }

        context("when the active library changes") {
            it("TC-42: does not show one library's covers in another") {
                withFilesDir { filesDir ->
                    val libraryA = randomString()
                    val libraryB = "other-${randomString()}"
                    val active = TestActiveLibrary(libraryA)
                    val store = ActiveLibraryCoverDiskStore(active, LibraryCovers(filesDir))
                    val key = randomString()
                    val cover = randomCover()
                    store.write(key, cover)

                    active.current = libraryB

                    store.read(key).shouldBeNull()
                    active.current = libraryA
                    store.read(key) shouldBe cover
                }
            }
        }

        context("when there is no active library") {
            it("TC-43: misses on read and drops the write without creating a covers directory") {
                withFilesDir { filesDir ->
                    val covers = LibraryCovers(filesDir)
                    val store = ActiveLibraryCoverDiskStore(TestActiveLibrary(), covers)
                    val key = randomString()

                    store.write(key, randomCover())

                    store.read(key).shouldBeNull()
                    covers.root.exists() shouldBe false
                }
            }
        }

        context("when the library is set after the store is built") {
            it("TC-44: works once a library exists") {
                withFilesDir { filesDir ->
                    val library = randomString()
                    val active = TestActiveLibrary()
                    val store = ActiveLibraryCoverDiskStore(active, LibraryCovers(filesDir))
                    val key = randomString()
                    val cover = randomCover()

                    active.current = library
                    store.write(key, cover)

                    store.read(key) shouldBe cover
                }
            }
        }
    }
})
