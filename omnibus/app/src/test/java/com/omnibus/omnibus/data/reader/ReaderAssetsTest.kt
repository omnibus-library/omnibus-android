package com.omnibus.omnibus.data.reader

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderAssetsTest : DescribeSpec({

    describe("assetPathFor") {

        it("TC-208: ignores a leading slash") {
            ReaderAssets.assetPathFor("/reader.html") shouldBe "reader/reader.html"
        }

        it("TC-209: refuses an empty path, a nested path and a path that climbs out of the directory") {
            listOf("", "/", ".", "..", "../secret.json", "sub/reader.html", "..\\secret.json").forEach { path ->
                ReaderAssets.assetPathFor(path) shouldBe null
            }
        }
    }

    describe("mimeTypeFor") {

        it("TC-210: serves scripts as text/javascript and pages as text/html") {
            ReaderAssets.mimeTypeFor("reader/epub.min.js") shouldBe "text/javascript"
            ReaderAssets.mimeTypeFor("reader/jszip.min.js") shouldBe "text/javascript"
            ReaderAssets.mimeTypeFor("reader/epub-reader-glue.js") shouldBe "text/javascript"
            ReaderAssets.mimeTypeFor("reader/reader.html") shouldBe "text/html"
        }

        it("TC-211: serves stylesheets and fonts with their own types") {
            ReaderAssets.mimeTypeFor("reader-fonts.css") shouldBe "text/css"
            ReaderAssets.mimeTypeFor("EBGaramond-Italic.woff2") shouldBe "font/woff2"
        }

        it("TC-212: matches the extension case-insensitively and falls back to a binary type") {
            ReaderAssets.mimeTypeFor("READER.HTML") shouldBe "text/html"
            ReaderAssets.mimeTypeFor("book.epub") shouldBe "application/octet-stream"
            ReaderAssets.mimeTypeFor("noextension") shouldBe "application/octet-stream"
        }
    }

    describe("bookUuidFor") {

        it("TC-226: extracts the uuid from a book file name") {
            ReaderAssets.bookUuidFor("0a1b2c3d-4e5f.epub") shouldBe "0a1b2c3d-4e5f"
            ReaderAssets.bookUuidFor("/0a1b2c3d-4e5f.epub") shouldBe "0a1b2c3d-4e5f"
        }

        it("TC-227: refuses names that could change the API path they are placed in") {
            listOf("", ".epub", "book.pdf", "a/b.epub", "../x.epub", "a b.epub", "a%2Fb.epub", "a.epub.js").forEach {
                ReaderAssets.bookUuidFor(it) shouldBe null
            }
        }
    }

    describe("URLs") {

        it("TC-213: puts the entry page, fonts and book on the one asset-loader origin") {
            ReaderAssets.ENTRY_URL shouldBe "https://appassets.androidplatform.net/reader/reader.html"
            ReaderAssets.FONTS_URL shouldBe "https://appassets.androidplatform.net/reader/reader-fonts.css"
            ReaderAssets.bookUrl("abc-123") shouldBe "https://appassets.androidplatform.net/book/abc-123.epub"
        }
    }
})
