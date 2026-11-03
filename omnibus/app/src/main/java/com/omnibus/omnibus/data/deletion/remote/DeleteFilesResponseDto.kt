package com.omnibus.omnibus.data.deletion.remote

import com.omnibus.omnibus.data.deletion.DeleteItemsResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The body of a 2xx `POST api/books/{uuid}/delete-files`. Missing fields read as empty lists and false.
 */
@Serializable
internal data class DeleteFilesResponseDto(
    @SerialName("deleted_file_ids") val deletedFileIds: List<Long> = emptyList(),
    @SerialName("deleted_copy_ids") val deletedCopyIds: List<Long> = emptyList(),
    @SerialName("book_deleted") val bookDeleted: Boolean = false,
) {
    fun toDomain(): DeleteItemsResult.Deleted = DeleteItemsResult.Deleted(
        deletedFileIds = deletedFileIds,
        deletedCopyIds = deletedCopyIds,
        bookDeleted = bookDeleted,
    )
}
