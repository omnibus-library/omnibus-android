package com.omnibus.omnibus.data.configuration

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Rules for turning what a user types into a server address. */
internal object ServerAddress {

    /**
     * Trims [address], assumes `http://` when no scheme is given, and drops trailing slashes.
     *
     * @return the normalized address, or null if it isn't a valid HTTP(S) URL.
     */
    fun normalize(address: String): String? {
        var normalized = address.trim()
        if (normalized.isEmpty()) return null
        if (!normalized.contains("://")) {
            normalized = "http://$normalized"
        }
        normalized = normalized.trimEnd('/')
        return normalized.takeIf { it.toHttpUrlOrNull() != null }
    }
}
