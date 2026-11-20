package com.omnibus.omnibus.feature.scan

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.scan.ScanBook

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
