package com.omnibus.omnibus.data.reader

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderAssetsTest : DescribeSpec({

    describe("assetPathFor") {

        it("TC-207: maps each reader file to its path under assets/reader") {
            listOf(
                "reader.html",
                "epub.min.js",
                "jszip.min.js",
                "epub-reader-glue.js",
                "reader-fonts.css",
                "EBGaramond-Regular.woff2",
            ).forEach { name ->
                ReaderAssets.assetPathFor(name) shouldBe "reader/$name"
            }
        }

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

    describe("URLs") {

        it("TC-213: puts the entry page, fonts and book on the one asset-loader origin") {
            ReaderAssets.ENTRY_URL shouldBe "https://appassets.androidplatform.net/reader/reader.html"
            ReaderAssets.FONTS_URL shouldBe "https://appassets.androidplatform.net/reader/reader-fonts.css"
            ReaderAssets.bookUrl("abc-123") shouldBe "https://appassets.androidplatform.net/book/abc-123.epub"
        }
    }
})
