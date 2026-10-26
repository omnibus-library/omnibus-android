package com.omnibus.omnibus.data.cover.local

import coil3.disk.DiskCache
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toOkioPath
import java.nio.file.Files

class DefaultCoverDiskStoreTest : DescribeSpec({

    lateinit var directory: java.io.File

    fun newCache(): DiskCache =
        DiskCache.Builder()
            .directory(directory.toOkioPath())
            .maxSizeBytes(1024 * 1024)
            .build()

    beforeEach { directory = Files.createTempDirectory("covers").toFile() }

    afterEach { directory.deleteRecursively() }

    describe("read") {

        it("TC-138: returns null for a key that was never written") {
            DefaultCoverDiskStore(newCache()).read("missing").shouldBeNull()
        }

        it("TC-139: returns what was written, with its validator and timestamp") {
            val store = DefaultCoverDiskStore(newCache())
            val cover = CachedCover("webp".encodeUtf8(), etag = "\"v1\"", storedAtMillis = 1234)

            store.write("key", cover)

            store.read("key") shouldBe cover
        }

        it("TC-140: returns an entry stored without a validator") {
            val store = DefaultCoverDiskStore(newCache())
            val cover = CachedCover("webp".encodeUtf8(), etag = null, storedAtMillis = 99)

            store.write("key", cover)

            store.read("key") shouldBe cover
        }

        it("TC-141: survives a restart") {
            DefaultCoverDiskStore(newCache()).write(
                "key",
                CachedCover("webp".encodeUtf8(), "\"v1\"", 5),
            )

            DefaultCoverDiskStore(newCache()).read("key") shouldBe
                CachedCover("webp".encodeUtf8(), "\"v1\"", 5)
        }
    }

    describe("write") {

        it("TC-142: replaces the previous entry, dropping its validator") {
            val store = DefaultCoverDiskStore(newCache())
            store.write("key", CachedCover("old".encodeUtf8(), "\"v1\"", 1))

            store.write("key", CachedCover("new".encodeUtf8(), null, 2))

            store.read("key") shouldBe CachedCover("new".encodeUtf8(), null, 2)
        }
    }

    describe("remove") {

        it("TC-143: drops the entry and its validator") {
            val store = DefaultCoverDiskStore(newCache())
            store.write("key", CachedCover("webp".encodeUtf8(), "\"v1\"", 1))

            store.remove("key")

            store.read("key").shouldBeNull()
        }
    }

    describe("clear") {

        it("TC-144: drops every entry") {
            val store = DefaultCoverDiskStore(newCache())
            store.write("a", CachedCover("a".encodeUtf8(), null, 1))
            store.write("b", CachedCover("b".encodeUtf8(), null, 1))

            store.clear()

            store.read("a").shouldBeNull()
            store.read("b").shouldBeNull()
        }
    }

    describe("CoverMetadata") {

        it("TC-145: round-trips") {
            CoverMetadata.decode(CoverMetadata.encode(CoverMetadata("\"v1\"", 42))) shouldBe
                CoverMetadata("\"v1\"", 42)
            CoverMetadata.decode(CoverMetadata.encode(CoverMetadata(null, 42))) shouldBe
                CoverMetadata(null, 42)
        }

        it("TC-146: treats an unreadable sidecar as stale and validator-less") {
            CoverMetadata.decode("garbage") shouldBe CoverMetadata(null, 0)
            CoverMetadata.decode("") shouldBe CoverMetadata(null, 0)
        }
    }
})
