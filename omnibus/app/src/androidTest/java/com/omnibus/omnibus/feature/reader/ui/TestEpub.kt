package com.omnibus.omnibus.feature.reader.ui

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds a small valid EPUB 3 in memory, long enough to run to many pages. */
object TestEpub {

    fun build(chapters: Int = 3, paragraphsPerChapter: Int = 60): ByteArray {
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
                val body = (1..paragraphsPerChapter).joinToString("\n") { paragraph ->
                    "<p>Chapter $chapter paragraph $paragraph. " +
                        "The quick brown fox jumps over the lazy dog while the reader turns another page.</p>"
                }
                zip.add(
                    "OEBPS/chapter$chapter.xhtml",
                    """<?xml version="1.0" encoding="utf-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter $chapter</title></head>
                      <body><h1>Chapter $chapter</h1>
                      $body</body>
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
}
