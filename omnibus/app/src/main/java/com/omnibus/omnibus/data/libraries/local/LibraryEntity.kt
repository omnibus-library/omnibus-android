package com.omnibus.omnibus.data.libraries.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.omnibus.omnibus.data.libraries.Library

/** A row of the `libraries` table. Each row is one server this device keeps a library for. */
@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val address: String,
    val name: String,
    val username: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)

internal fun LibraryEntity.toLibrary(): Library = Library(
    id = id,
    address = address,
    name = name,
    username = username,
    addedAt = addedAt,
    lastUsedAt = lastUsedAt,
    isActive = isActive,
)

internal fun Library.toEntity(): LibraryEntity = LibraryEntity(
    id = id,
    address = address,
    name = name,
    username = username,
    addedAt = addedAt,
    lastUsedAt = lastUsedAt,
    isActive = isActive,
)
