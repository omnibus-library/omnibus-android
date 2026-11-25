package com.omnibus.omnibus.data.configuration

import android.util.Log
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.network.ServerBaseUrlProvider
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Reads the base URL from the active library's server address. */
class SavedServerBaseUrlProvider @Inject constructor(
    private val librariesRepository: LibrariesRepository,
) : ServerBaseUrlProvider {

    override suspend fun baseUrl(): HttpUrl? {
        val saved = try {
            librariesRepository.getActiveLibraryFlow().first()?.address
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to read the saved server address", ex)
            null
        }
        // Normalized on read too, since addresses saved by older builds are stored as typed.
        return saved?.let(ServerAddress::normalize)?.toHttpUrl()
    }

    private companion object {
        const val TAG = "SavedServerBaseUrlProvider"
    }
}
