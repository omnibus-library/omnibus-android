package com.omnibus.omnibus.data.cover.local

import coil3.disk.DiskCache
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.File
import java.nio.file.Files
import okio.Path.Companion.toOkioPath

private inline fun <T> withDirectory(block: (File) -> T): T {
    val directory = Files.createTempDirectory("covers").toFile()
    return try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

class DefaultCoverDiskStoreTest : DescribeSpec({

    fun sut(directory: File): DefaultCoverDiskStore = DefaultCoverDiskStore(
        DiskCache.Builder()
            .directory(directory.toOkioPath())
            .maxSizeBytes(1024 * 1024)
            .build(),
    )

    describe("reading an entry") {
        context("when the key was never written") {
            it("TC-138: returns null") {
                withDirectory { directory ->
                    sut(directory).read(randomString()).shouldBeNull()
                }
            }
        }

        context("when the entry was written with a validator") {
            it("TC-139: returns what was written") {
                withDirectory { directory ->
                    val key = randomString()
                    val cover = randomCachedCover().copy(etag = "\"${randomString()}\"")
                    val store = sut(directory)

                    store.write(key, cover)

                    store.read(key) shouldBe cover
                }
            }
        }

        context("when the entry was written without a validator") {
            it("TC-140: returns what was written") {
                withDirectory { directory ->
                    val key = randomString()
                    val cover = randomCachedCover().copy(etag = null)
                    val store = sut(directory)

                    store.write(key, cover)

                    store.read(key) shouldBe cover
                }
            }
        }

        context("when the app has restarted") {
            it("TC-141: returns what was written") {
                withDirectory { directory ->
                    val key = randomString()
                    val cover = randomCachedCover()

                    sut(directory).write(key, cover)

                    sut(directory).read(key) shouldBe cover
                }
            }
        }
    }

    describe("writing an entry") {
        context("when the key already has one") {
            it("TC-142: replaces it, validator included") {
                withDirectory { directory ->
                    val key = randomString()
                    val replacement = randomCachedCover().copy(etag = null)
                    val store = sut(directory)
                    store.write(key, randomCachedCover().copy(etag = "\"${randomString()}\""))

                    store.write(key, replacement)

                    store.read(key) shouldBe replacement
                }
            }
        }
    }

    describe("removing an entry") {
        it("TC-143: drops it") {
            withDirectory { directory ->
                val key = randomString()
                val store = sut(directory)
                store.write(key, randomCachedCover())

                store.remove(key)

                store.read(key).shouldBeNull()
            }
        }
    }

    describe("clearing the store") {
        it("TC-144: drops every entry") {
            withDirectory { directory ->
                val keys = listOf(randomString(8..16), randomString(8..16))
                val store = sut(directory)
                keys.forEach { store.write(it, randomCachedCover()) }

                store.clear()

                keys.map { store.read(it) } shouldBe listOf(null, null)
            }
        }
    }

    describe("decoding an entry's sidecar") {
        context("when the sidecar is unreadable") {
            it("TC-146: treats the entry as stale without a validator, for garbage and empty sidecars") {
                checkAll(Exhaustive.of("garbage", "")) { raw ->
                    CoverMetadata.decode(raw) shouldBe CoverMetadata(null, 0)
                }
            }
        }
    }
})
