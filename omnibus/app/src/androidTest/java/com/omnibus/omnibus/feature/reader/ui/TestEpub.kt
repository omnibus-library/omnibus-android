package com.omnibus.omnibus.feature.reader.ui

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds a small valid EPUB 3 in memory, long enough to run to many pages. */
object TestEpub {

    /**
     * Builds the book. With [hiddenMarkup], chapter 1 opens with markup that must never reach a selection's
     * text: a `display:none` page-break span welded into a word, a `[hidden]` block and an in-body style.
     * A non-empty [stylesheet] goes in every chapter's head, the way a publisher's CSS would.
     */
    fun build(
        chapters: Int = 3,
        paragraphsPerChapter: Int = 60,
        hiddenMarkup: Boolean = false,
        stylesheet: String = "",
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // The mimetype entry must come first and be stored uncompressed.
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                },
            )
            zip.write(mimetype)
            zip.closeEntry()

            zip.add(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""".trimIndent(),
            )
            val items = (1..chapters).joinToString("\n") {
                """<item id="c$it" href="chapter$it.xhtml" media-type="application/xhtml+xml"/>"""
            }
            val spine = (1..chapters).joinToString("\n") { """<itemref idref="c$it"/>""" }
            zip.add(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="id">urn:uuid:omnibus-test-book</dc:identifier>
                    <dc:title>Test Book</dc:title>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    $items
                  </manifest>
                  <spine>$spine</spine>
                </package>""".trimIndent(),
            )
            val navItems = (1..chapters).joinToString("\n") { """<li><a href="chapter$it.xhtml">Chapter $it</a></li>""" }
            zip.add(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <head><title>Contents</title></head>
                  <body><nav epub:type="toc"><ol>$navItems</ol></nav></body>
                </html>""".trimIndent(),
            )
            for (chapter in 1..chapters) {
                val marked = hiddenMarkup && chapter == 1
                val body = (1..paragraphsPerChapter).joinToString("\n") { paragraph ->
                    "<p>Chapter $chapter paragraph $paragraph. " +
                        "The quick brown fox jumps over the lazy dog while the reader turns another page.</p>"
                }
                val html = if (marked) """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">""" else """<html xmlns="http://www.w3.org/1999/xhtml">"""
                val hidden = if (marked) HIDDEN_MARKUP else ""
                val style = if (stylesheet.isEmpty()) "" else "<style>$stylesheet</style>"
                zip.add(
                    "OEBPS/chapter$chapter.xhtml",
                    """<?xml version="1.0" encoding="utf-8"?>
                    $html
                      <head><title>Chapter $chapter</title>$style</head>
                      <body><h1>Chapter $chapter</h1>
                      $hidden$body</body>
                    </html>""".trimIndent(),
                )
            }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.add(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    /** Opens chapter 1 with `omn-start` ("wor" + hidden "PAGEMARK" + "ld") and `omn-end` ("finish") for the tests to aim at. */
    private const val HIDDEN_MARKUP =
        "<p>Alpha <span id=\"omn-start\">wor</span><span epub:type=\"pagebreak\" style=\"display:none\">PAGEMARK</span>" +
            "ld beta.</p><style>.omn-css{color:red}</style><div hidden=\"hidden\">HIDDENBLOCK</div>" +
            "<p>Omega <span id=\"omn-end\">finish</span> line.</p>\n"
}
