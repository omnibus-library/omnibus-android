package com.omnibus.omnibus.feature.libraries.manage

/** Longest library name, in characters (code points). */
const val LIBRARY_NAME_MAX_LENGTH = 40

/** This string cut to [LIBRARY_NAME_MAX_LENGTH] code points, never splitting a surrogate pair. Not trimmed. */
fun String.capLibraryName(): String =
    if (codePointCount(0, length) <= LIBRARY_NAME_MAX_LENGTH) {
        this
    } else {
        substring(0, offsetByCodePoints(0, LIBRARY_NAME_MAX_LENGTH))
    }
