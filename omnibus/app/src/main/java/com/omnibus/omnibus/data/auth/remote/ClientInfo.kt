package com.omnibus.omnibus.data.auth.remote

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Describes this client to the server when it signs in or registers. */
interface ClientInfo {
    /** Human-readable device name, shown in the server's session list. */
    val deviceName: String

    /** The app's version name. */
    val clientVersion: String
}

class DefaultClientInfo @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ClientInfo {

    override val deviceName: String
        get() = Build.MODEL.orEmpty().ifBlank { DEFAULT_DEVICE_NAME }

    override val clientVersion: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?: DEFAULT_CLIENT_VERSION

    private companion object {
        const val DEFAULT_DEVICE_NAME = "Android"
        const val DEFAULT_CLIENT_VERSION = "1.0"
    }
}
