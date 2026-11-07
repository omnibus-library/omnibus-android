package com.omnibus.omnibus.data.configuration

import com.omnibus.omnibus.testing.randomString

/** A valid server address on its own host, lowercase as `HttpUrl` keeps it. */
fun randomServerAddress(): String = "https://${randomString(8..16).lowercase()}.example.com"
