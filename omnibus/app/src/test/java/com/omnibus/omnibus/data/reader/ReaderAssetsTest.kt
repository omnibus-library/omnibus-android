package com.omnibus.omnibus.data.reader

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.net.URI

class ReaderAssetsTest : DescribeSpec({

    describe("finding a bundled reader file") {

        it("TC-208: finds a plain file name regardless of a leading slash") {
            val name = randomString()

            checkAll(Exhaustive.of(name, "/$name")) { path ->
                ReaderAssets.assetPathFor(path) shouldBe "reader/$name"
            }
        }

        it("TC-209: refuses any path that is empty, nests or climbs out of the directory") {
            checkAll(Exhaustive.of("", "/", ".", "..", "../secret.json", "sub/reader.html", "..\\secret.json")) { path ->
                ReaderAssets.assetPathFor(path).shouldBeNull()
            }
        }
    }

    describe("choosing the type a file is served with") {

        it("TC-210, TC-211, TC-212: serves each file kind the reader bundles with its own type, in any case") {
            checkAll(
                Exhaustive.of(
                    "epub.min.js" to "text/javascript",
                    "reader.html" to "text/html",
                    "reader-fonts.css" to "text/css",
                    "EBGaramond-Italic.woff2" to "font/woff2",
                    "READER.HTML" to "text/html",
                ),
            ) { (fileName, type) ->
                ReaderAssets.mimeTypeFor(fileName) shouldBe type
            }
        }

        it("TC-212: falls back to a binary type regardless of why the extension is unknown") {
            checkAll(Exhaustive.of("book.epub", "noextension")) { fileName ->
                ReaderAssets.mimeTypeFor(fileName) shouldBe "application/octet-stream"
            }
        }
    }

    describe("reading the book uuid from a request path") {

        it("TC-226: extracts the uuid regardless of a leading slash") {
            checkAll(Exhaustive.of("0a1b2c3d-4e5f.epub", "/0a1b2c3d-4e5f.epub")) { path ->
                ReaderAssets.bookUuidFor(path) shouldBe "0a1b2c3d-4e5f"
            }
        }

        it("TC-227: refuses any name that could change the API path it is placed in") {
            checkAll(Exhaustive.of("", ".epub", "book.pdf", "a/b.epub", "../x.epub", "a b.epub", "a%2Fb.epub", "a.epub.js")) { path ->
                ReaderAssets.bookUuidFor(path).shouldBeNull()
            }
        }
    }

    describe("the reader's URLs") {

        it("TC-213: puts the entry page, fonts and book on the asset loader's origin") {
            checkAll(Exhaustive.of(ReaderAssets.ENTRY_URL, ReaderAssets.FONTS_URL, ReaderAssets.bookUrl(randomString()))) { url ->
                URI(url).scheme shouldBe "https"
                URI(url).host shouldBe "appassets.androidplatform.net"
            }
        }

        it("TC-213: gives a book URL whose path reads back as that book's uuid") {
            val uuid = randomString()

            val path = URI(ReaderAssets.bookUrl(uuid)).path.removePrefix(ReaderAssets.BOOK_PATH)

            ReaderAssets.bookUuidFor(path) shouldBe uuid
        }
    }
})
