package com.omnibus.omnibus.data.auth

import com.omnibus.omnibus.data.auth.remote.UserInfoDto

/** The signed-in user's id and permissions, as the server last reported them. */
data class CurrentUser(
    val id: Long,
    val canEdit: Boolean,
    val canUpload: Boolean,
    /** Lowercase names of the formats this user has hidden. */
    val hiddenFormats: Set<String>,
)

internal fun UserInfoDto.toCurrentUser(): CurrentUser = CurrentUser(
    id = id,
    canEdit = canEdit,
    canUpload = canUpload,
    hiddenFormats = hiddenFormats.map { it.lowercase() }.toSet(),
)
