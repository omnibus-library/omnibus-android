package com.omnibus.omnibus.data.auth

import com.omnibus.omnibus.data.auth.remote.UserInfoDto

/** The signed-in user's id and permissions, as the server last reported them. */
data class CurrentUser(
    val id: Long,
    val canEdit: Boolean,
    val canUpload: Boolean,
    /** Lowercase names of the formats this user has hidden. */
    val hiddenFormats: Set<String>,
    /** Whether this user is an administrator. */
    val isAdmin: Boolean = false,
    /** Whether this user may download books. */
    val canDownload: Boolean = false,
    /** The user's Kindle address for sending books, if one is set. */
    val kindleEmail: String? = null,
)

internal fun UserInfoDto.toCurrentUser(): CurrentUser = CurrentUser(
    id = id,
    canEdit = canEdit,
    canUpload = canUpload,
    hiddenFormats = hiddenFormats.map { it.lowercase() }.toSet(),
    isAdmin = isAdmin,
    canDownload = canDownload,
    kindleEmail = kindleEmail,
)
