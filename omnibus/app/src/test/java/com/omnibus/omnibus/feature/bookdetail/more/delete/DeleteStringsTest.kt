package com.omnibus.omnibus.feature.bookdetail.more.delete

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class DeleteStringsTest : DescribeSpec({

    val appDir = File(System.getProperty("user.dir"))
    val deleteStrings = File(appDir, "src/main/res/values/strings_book_detail_delete.xml")
    val stringRegex = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    val pluralsRegex = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
    val itemRegex = Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)

    /** The plain strings in the file, by name. */
    val strings: Map<String, String> by lazy {
        stringRegex.findAll(deleteStrings.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** The plurals in the file, by name, each as its quantity-to-text map. */
    val plurals: Map<String, Map<String, String>> by lazy {
        pluralsRegex.findAll(deleteStrings.readText()).associate { match ->
            match.groupValues[1] to itemRegex.findAll(match.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
        }
    }

    /** A plain string, formatted with [args] as Android formats it. */
    fun string(name: String, vararg args: Any): String = String.format(Locale.US, strings.getValue(name), *args)

    /** A plurals entry for [count], with the `one` quantity for 1 and `other` otherwise, formatted with [count]. */
    fun quantity(name: String, count: Int): String {
        val forms = plurals.getValue(name)
        val text = forms[if (count == 1) "one" else "other"] ?: forms.getValue("other")
        return String.format(Locale.US, text, count)
    }

    describe("the delete strings") {

        it("#121 TC-15: the formatted strings match the copy exactly") {
            quantity("book_detail_delete_heading_all_files", 2) shouldBe "Delete all 2 files?"
            quantity("book_detail_delete_heading_some_files", 1) shouldBe "Delete 1 file?"
            string("book_detail_delete_heading_name", "The Sign of the Four") shouldBe "Delete “The Sign of the Four”?"
            string("book_detail_delete_choose_title", "The Sign of the Four") shouldBe
                "Delete files from “The Sign of the Four”?"
            string("book_detail_delete_row_files") shouldBe "Delete files…"
            string("book_detail_delete_row_record") shouldBe "Delete record…"
            quantity("book_detail_delete_choose_files", 2) shouldBe "Delete 2 files…"
        }

        it("#121 TC-16: every name carries the book_detail_delete_ prefix, the file is well-formed, and the wording variants hold") {
            val names = strings.keys + plurals.keys
            names.all { it.startsWith("book_detail_delete_") } shouldBe true

            // Throws on malformed XML, so a parse is the well-formedness check.
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(deleteStrings)

            strings.getValue("book_detail_delete_a11y_destructive") shouldBe "delete permanently"
            quantity("book_detail_delete_heading_all_items", 3) shouldBe "Delete all 3 items?"
            quantity("book_detail_delete_choose_items", 2) shouldBe "Delete 2 items…"
            quantity("book_detail_delete_heading_some_items", 1) shouldBe "Delete 1 item?"

            // aapt trims the spaces at both ends of a string, so these two are wrapped in literal double quotes.
            strings.getValue("book_detail_delete_list_separator") shouldBe "\", \""
            strings.getValue("book_detail_delete_list_last") shouldBe "\" and \""
            strings.getValue("book_detail_delete_list_separator").removeSurrounding("\"") shouldBe ", "
            strings.getValue("book_detail_delete_list_last").removeSurrounding("\"") shouldBe " and "
        }
    }
})
