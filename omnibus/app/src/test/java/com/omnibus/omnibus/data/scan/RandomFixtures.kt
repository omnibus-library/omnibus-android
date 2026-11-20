package com.omnibus.omnibus.data.scan

import com.omnibus.omnibus.testing.TEST_RANDOM
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A library book with every field randomized, including whether each optional one is present. */
fun randomScanBook(): ScanBook = ScanBook(
    uuid = randomString(),
    title = randomString(),
    authors = List(randomInt(0..3)) { randomString() },
    coverUrl = if (randomBoolean()) "https://covers.example/${randomString()}.jpg" else null,
    hasPhysical = randomBoolean(),
    hasFiles = randomBoolean(),
    isbn = if (randomBoolean()) randomValidIsbn13() else null,
)

/**
 * A provider's record with every field randomized, including whether each optional one is present. Its raw object
 * also carries a key the app doesn't model, so a write can prove the record is sent back whole.
 */
fun randomProviderRecord(): ProviderRecord {
    val isbn13 = randomValidIsbn13()
    val title = randomString()
    val authors = List(randomInt(0..3)) { randomString() }
    val year = if (randomBoolean()) randomString() else null
    val firstPublishYear = if (randomBoolean()) randomLong(1000L..2100L) else null
    val publisher = if (randomBoolean()) randomString() else null
    val pages = if (randomBoolean()) randomLong(1L..2000L) else null
    val series = if (randomBoolean()) randomString() else null
    val coverUrl = if (randomBoolean()) "https://covers.example/${randomString()}.jpg" else null
    val raw = buildJsonObject {
        put("isbn13", isbn13)
        put("title", title)
        putJsonArray("authors") { authors.forEach { add(JsonPrimitive(it)) } }
        put("source", randomOf(listOf("open_library", "google_books", "hardcover")))
        year?.let { put("year", it) }
        firstPublishYear?.let { put("first_publish_year", it) }
        publisher?.let { put("publisher", it) }
        pages?.let { put("pages", it) }
        series?.let { put("series", it) }
        coverUrl?.let { put("cover_url", it) }
        put("unmodelled", true)
    }
    return ProviderRecord(raw, isbn13, title, authors, year, firstPublishYear, publisher, pages, series, coverUrl)
}

/** A valid ISBN-13 with a 978 or 979 prefix, as a barcode carries, with random digits and a correct check digit. */
fun randomValidIsbn13(): String = isbn13WithCheckDigit(randomOf(listOf("978", "979")) + randomDigits(9))

/** A valid ISBN-10 with random digits and a correct check digit, which is `X` when the check value is 10. */
fun randomValidIsbn10(): String {
    val body = randomDigits(9)
    val sum = body.mapIndexed { index, c -> (c - '0') * (10 - index) }.sum()
    val check = (11 - sum % 11) % 11
    return body + if (check == 10) "X" else check.toString()
}

/** The 12 digits [body] followed by the ISBN-13 (EAN-13) check digit that makes the whole number valid. */
fun isbn13WithCheckDigit(body: String): String {
    val sum = body.mapIndexed { index, c -> (c - '0') * (if (index % 2 == 0) 1 else 3) }.sum()
    return body + ((10 - sum % 10) % 10)
}

/** [length] random decimal digits. */
fun randomDigits(length: Int): String = (0 until length).map { TEST_RANDOM.nextInt(10) }.joinToString("")
