package com.omnibus.omnibus.data.network

import okhttp3.HttpUrl

/** [ServerBaseUrlProvider] that returns whatever [baseUrl] is set to. */
class TestServerBaseUrlProvider(var baseUrl: HttpUrl? = null) : ServerBaseUrlProvider {
    override suspend fun baseUrl(): HttpUrl? = baseUrl
}
