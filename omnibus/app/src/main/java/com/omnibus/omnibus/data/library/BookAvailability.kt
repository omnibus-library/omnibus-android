package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import com.omnibus.omnibus.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Decides whether a book can be opened right now, with or without the server. */
interface BookAvailability {

    /** True when [bookUuid] can be opened for [medium]: it is downloaded, or the device is online. */
    suspend fun canOpen(bookUuid: String, medium: ProgressFormat): Boolean
}

/**
 * Checks the downloads on disk and the live connectivity state. Mirrors iOS `DownloadManager.canOpen`.
 * Listening is never downloaded yet, so an audiobook opens only while online.
 */
class DefaultBookAvailability @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val localBookFiles: LocalBookFiles,
    private val connectivity: ConnectivityMonitor,
) : BookAvailability {

    override suspend fun canOpen(bookUuid: String, medium: ProgressFormat): Boolean {
        val downloaded = medium == ProgressFormat.Reading &&
            withContext(ioDispatcher) { localBookFiles.find(bookUuid) != null }
        return downloaded || connectivity.isOnline.value
    }
}
