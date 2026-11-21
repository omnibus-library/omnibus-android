package com.omnibus.omnibus.feature.scan

import com.omnibus.omnibus.data.scan.randomProviderRecord
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class ProviderFactsTest : DescribeSpec({

    describe("the provider's fact line") {

        it("#336 TC-45: keeps the facts the provider gave, in order") {
            val presence = listOf(false, true).flatMap { year ->
                listOf(false, true).flatMap { publisher -> listOf(false, true).map { pages -> Triple(year, publisher, pages) } }
            }
            checkAll(Exhaustive.collection(presence)) { (hasYear, hasPublisher, hasPages) ->
                val year = randomInt(1000..2100)
                val publisher = randomString()
                val pages = randomLong(1L..2000L)
                val record = randomProviderRecord().copy(
                    year = null,
                    firstPublishYear = if (hasYear) year.toLong() else null,
                    publisher = if (hasPublisher) publisher else null,
                    pages = if (hasPages) pages else null,
                    series = randomString(),
                )

                providerFactParts(record, firstPublished = { "published $it" }, pages = { "$it pages" }) shouldBe listOfNotNull(
                    if (hasYear) "published $year" else null,
                    if (hasPublisher) publisher else null,
                    if (hasPages) "$pages pages" else null,
                )
            }
        }

        it("#336 TC-45: falls back to the edition's year when no first-published year is given") {
            val year = randomString()
            val record = randomProviderRecord().copy(year = year, firstPublishYear = null, publisher = null, pages = null)

            providerFactParts(record, firstPublished = { "published $it" }, pages = { "$it pages" }) shouldBe listOf("published $year")
        }
    }
})
