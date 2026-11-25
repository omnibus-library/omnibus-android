package com.omnibus.omnibus.data.libraries

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/** The exclusions the rules must keep: the covers, the auth keyset and the token. */
private val REQUIRED_EXCLUSIONS = listOf(
    "file" to "covers/",
    "sharedpref" to "omnibus_auth_keyset.xml",
    "file" to "datastore/auth_token.preferences_pb",
)

private fun parse(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))

private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

/** The (domain, path) of every exclude under [root]. */
private fun Element.exclusions(): List<Pair<String, String>> =
    getElementsByTagName("exclude").elements().map { it.getAttribute("domain") to it.getAttribute("path") }

private fun Element.includeCount(): Int = getElementsByTagName("include").length

class BackupRulesTest : DescribeSpec({

    describe("the full backup rules") {
        val root = parse("src/main/res/xml/backup_rules.xml").documentElement

        it("TC-63: excludes the covers, the auth keyset and the token") {
            root.exclusions() shouldContainExactlyInAnyOrder REQUIRED_EXCLUSIONS
        }

        it("TC-63: has no include, so omnibus.db stays backed up") {
            root.includeCount() shouldBe 0
        }
    }

    describe("the data extraction rules") {
        val root = parse("src/main/res/xml/data_extraction_rules.xml").documentElement
        val cloud = root.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = root.getElementsByTagName("device-transfer").item(0) as Element

        it("TC-64: excludes the covers, the auth keyset and the token from cloud backup") {
            cloud.exclusions() shouldContainExactlyInAnyOrder REQUIRED_EXCLUSIONS
        }

        it("TC-64: excludes the covers, the auth keyset and the token from device transfer") {
            transfer.exclusions() shouldContainExactlyInAnyOrder REQUIRED_EXCLUSIONS
        }

        it("TC-64: has no include in either section, so omnibus.db stays backed up") {
            cloud.includeCount() shouldBe 0
            transfer.includeCount() shouldBe 0
        }
    }
})
