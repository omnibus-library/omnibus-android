package com.omnibus.omnibus.data.auth.remote

/** Fixed [ClientInfo] for tests. */
class FakeClientInfo(
    override val deviceName: String = "Pixel 9",
    override val clientVersion: String = "1.2.3",
) : ClientInfo
