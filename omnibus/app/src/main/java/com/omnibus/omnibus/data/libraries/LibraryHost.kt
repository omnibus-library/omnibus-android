package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.configuration.ServerAddress
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The name of a library: the server's host, with its port when the address states one (C4). */
object LibraryHost {

    /**
     * The host of [address], plus `:port` only when the address itself states a port, so `http://host:80`
     * keeps its `:80`. An IPv6 host keeps its brackets. An address that doesn't parse is returned trimmed.
     */
    fun of(address: String): String {
        val trimmed = address.trim()
        val url = trimmed.toHttpUrlOrNull()
            ?: ServerAddress.normalize(address)?.toHttpUrlOrNull()
            ?: return trimmed
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        val port = statedPort(trimmed) ?: return host
        return "$host:$port"
    }

    /** The port written in the authority of [address], or null when it states none. */
    private fun statedPort(address: String): String? {
        val afterScheme = address.substringAfter("://", address)
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
        return PORT.find(authority)?.groupValues?.get(1)
    }

    private val PORT = Regex(""":(\d+)$""")
}
