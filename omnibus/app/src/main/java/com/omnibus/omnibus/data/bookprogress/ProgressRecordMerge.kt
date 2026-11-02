package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.progress.ReadingPosition

/**
 * The records for one book with this device's position merged in. Every non-Reading server record stays as it is. For
 * Reading, the newer of the device position and the server record wins by ordering clock, and a tie keeps the server's,
 * which carries `resolved`. The output is Reading first, then Listening.
 */
internal fun mergeProgressRecords(local: ReadingPosition?, server: List<ProgressRecord>): List<ProgressRecord> {
    val serverReading = server.firstOrNull { it.format == ProgressFormat.Reading }
    val localReading = local?.let {
        ProgressRecord(
            bookUuid = it.bookUuid,
            format = ProgressFormat.Reading,
            epubCfi = it.cfi,
            audioPositionSeconds = null,
            progressPercent = it.percent?.toDouble(),
            updatedAt = it.clientUpdatedAt,
            clientUpdatedAt = it.clientUpdatedAt,
            bookFileId = null,
            totalDurationSeconds = null,
            resolved = null,
        )
    }
    val reading = when {
        localReading == null -> serverReading
        serverReading == null -> localReading
        localReading.updatedAt > (serverReading.clientUpdatedAt ?: serverReading.updatedAt) -> localReading
        else -> serverReading
    }
    return listOfNotNull(reading, server.firstOrNull { it.format == ProgressFormat.Listening })
}
