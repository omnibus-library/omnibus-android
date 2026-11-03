package com.omnibus.omnibus.feature.bookdetail.more.export

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

class ExportStringsTest : DescribeSpec({

    val appDir = File(System.getProperty("user.dir"))
    val valuesDir = File(appDir, "src/main/res/values")
    val exportStrings = File(valuesDir, "strings_book_detail_export.xml")
    val stringRegex = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    // The raw XML values: apostrophes are escaped, and the ellipsis and curly apostrophe are the literal characters.
    val expected = mapOf(
        "book_detail_export_menu" to "Export…",
        "book_detail_export_title" to "Export",
        "book_detail_export_close" to "Close",
        "book_detail_export_send_section" to "Send to a device",
        "book_detail_export_save_section" to "Save a copy",
        "book_detail_export_kindle" to "Send to Kindle",
        "book_detail_export_kindle_sends_epub" to "Emails the EPUB to your Kindle",
        "book_detail_export_kindle_sends_book" to "Emails the book to your Kindle",
        "book_detail_export_kindle_send" to "Send",
        "book_detail_export_kindle_sending" to "Sending…",
        "book_detail_export_kindle_sent_meta" to "Sent to your Kindle.",
        "book_detail_export_kindle_sent" to "Sent",
        "book_detail_export_kindle_no_address" to "Add your Kindle address in Settings first.",
        "book_detail_export_kindle_settings" to "Settings",
        "book_detail_export_kindle_oversize" to "Too large to email. Upload it on Amazon’s Send to Kindle page, up to 200 MB.",
        "book_detail_export_kindle_open" to "Open",
        "book_detail_export_kindle_retry" to "Retry",
        "book_detail_export_kindle_failed_undelivered" to "The server couldn\\'t deliver this book.",
        "book_detail_export_kindle_failed_unconfirmed" to "Couldn\\'t confirm the send finished. Check your Kindle before trying again.",
        "book_detail_export_kindle_failed_not_configured" to "Email delivery isn\\'t set up on this server.",
        "book_detail_export_kindle_failed_not_found" to "This book isn\\'t on the server any more.",
        "book_detail_export_kindle_failed_unreachable" to "Couldn\\'t reach the server.",
        "book_detail_export_kindle_failed_unknown" to "Couldn\\'t send this book.",
        "book_detail_export_needs_connection" to "Needs a connection",
        "book_detail_export_kobo" to "Send to Kobo",
        "book_detail_export_kobo_meta" to "Saves a KEPUB to copy onto your Kobo",
        "book_detail_export_save" to "Save",
        "book_detail_export_saving" to "Saving…",
        "book_detail_export_download_format" to "Download %1\$s",
        "book_detail_export_download_audiobook" to "Download audiobook",
        "book_detail_export_opens_share_sheet" to "opens the share sheet",
        "book_detail_export_error_forbidden" to "Your account can\\'t download books.",
        "book_detail_export_error_not_found" to "This file isn\\'t on the server any more.",
        "book_detail_export_error_unreachable" to "Couldn\\'t reach the server.",
        "book_detail_export_error_no_space" to "There isn\\'t enough space on this device.",
        "book_detail_export_error_failed" to "Couldn\\'t download this file.",
    )

    describe("export sheet strings") {

        it("#118 TC-28: every export string has its exact raw value") {
            val values = stringRegex.findAll(exportStrings.readText())
                .associate { it.groupValues[1] to it.groupValues[2] }

            values shouldContainExactly expected
            expected.forEach { (name, raw) ->
                withClue(name) { values[name] shouldBe raw }
            }
        }

        it("#118 TC-29: names are exactly the expected set, prefixed book_detail_export_") {
            val text = exportStrings.readText()
            val names = stringRegex.findAll(text).map { it.groupValues[1] }.toList()

            names.all { it.startsWith("book_detail_export_") } shouldBe true
            names shouldContainExactlyInAnyOrder expected.keys
        }

        it("#118 TC-29: apostrophes are escaped in every export string") {
            val bodies = stringRegex.findAll(exportStrings.readText()).map { it.groupValues[2] }
            bodies.forEach { body ->
                Regex("""(?<!\\)'""").containsMatchIn(body) shouldBe false
            }
        }

        it("#118 TC-29: no other values file defines a book_detail_export_ name") {
            val others = valuesDir.listFiles { file -> file.name.endsWith(".xml") && file != exportStrings }.orEmpty()
            others.filter { it.readText().contains("name=\"book_detail_export_") } shouldBe emptyList()
        }
    }
})
