package com.omnibus.omnibus.feature.scan

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanBook
import kotlinx.serialization.json.JsonObject

/** The string [id] as the app shows it. */
internal fun scanText(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

/** A library book with the given facts. Its author is fixed, so a test can find the author line. */
internal fun scanBook(
    uuid: String = "book-1",
    title: String = "The Overstory",
    hasFiles: Boolean = true,
    hasCover: Boolean = false,
): ScanBook = ScanBook(
    uuid = uuid,
    title = title,
    authors = listOf("Richard Powers"),
    coverUrl = if (hasCover) "https://covers.example/book-1.jpg" else null,
    hasPhysical = false,
    hasFiles = hasFiles,
    isbn = "9780393636529",
)

/** A provider's record with the given facts. Its series, year, publisher and pages are set unless [withFacts] is false. */
internal fun scanRecord(
    title: String = "Piranesi",
    hasCover: Boolean = false,
    withFacts: Boolean = true,
): ProviderRecord = ProviderRecord(
    raw = JsonObject(emptyMap()),
    isbn13 = "9781635575636",
    title = title,
    authors = listOf("Susanna Clarke"),
    year = null,
    firstPublishYear = if (withFacts) 2020L else null,
    publisher = if (withFacts) "Bloomsbury" else null,
    pages = if (withFacts) 272L else null,
    series = if (withFacts) "The Kingkiller Chronicle" else null,
    coverUrl = if (hasCover) "https://covers.example/piranesi.jpg" else null,
)
