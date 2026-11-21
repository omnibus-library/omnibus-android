package com.omnibus.omnibus.data.scan

/** Why a typed ISBN can't be looked up. The UI maps each to a string. */
enum class IsbnRejection { WrongLength, InvalidCharacters, BadCheckDigit }

/** [raw] with everything but ASCII letters and digits removed, upper-cased. Hyphens and spaces disappear. */
fun cleanIsbn(raw: String): String = buildString {
    for (c in raw) {
        if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z') append(c.uppercaseChar())
    }
}

/**
 * Why [cleaned] (from [cleanIsbn]) is not a valid ISBN-10 or ISBN-13, or null when it is. Checks the length, then the
 * characters, then the check digit. A port of web `isbnRejection`.
 */
fun isbnRejection(cleaned: String): IsbnRejection? {
    if (cleaned.length != ISBN10_LENGTH && cleaned.length != ISBN13_LENGTH) return IsbnRejection.WrongLength
    val digits = IntArray(cleaned.length)
    cleaned.forEachIndexed { index, c ->
        digits[index] = when {
            c in '0'..'9' -> c - '0'
            c == 'X' && cleaned.length == ISBN10_LENGTH && index == ISBN10_LENGTH - 1 -> 10
            else -> return IsbnRejection.InvalidCharacters
        }
    }
    val valid = if (cleaned.length == ISBN10_LENGTH) isValidIsbn10(digits) else isValidIsbn13(digits)
    return if (valid) null else IsbnRejection.BadCheckDigit
}

/** Whether [value] is a book's barcode: 13 digits starting 978 or 979 with a valid EAN-13 check digit. */
fun isBookEan13(value: String): Boolean {
    if (value.length != ISBN13_LENGTH || !value.all { it in '0'..'9' }) return false
    if (!value.startsWith("978") && !value.startsWith("979")) return false
    return isValidIsbn13(IntArray(ISBN13_LENGTH) { value[it] - '0' })
}

private const val ISBN10_LENGTH = 10
private const val ISBN13_LENGTH = 13

private fun isValidIsbn10(digits: IntArray): Boolean =
    digits.indices.sumOf { digits[it] * (ISBN10_LENGTH - it) } % 11 == 0

private fun isValidIsbn13(digits: IntArray): Boolean =
    digits.indices.sumOf { digits[it] * (if (it % 2 == 0) 1 else 3) } % 10 == 0
