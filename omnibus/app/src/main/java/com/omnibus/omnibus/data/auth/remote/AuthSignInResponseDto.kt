package com.omnibus.omnibus.data.auth.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthSignInResponseDto(
    val user: UserInfoDto,
    val token: String,
) {
    companion object {
        fun fromJsonString(jsonString: String): AuthSignInResponseDto =
            Json.decodeFromString<AuthSignInResponseDto>(jsonString)

    }
}

@Serializable
data class UserInfoDto(
    val id: Long,
    val username: String,
    @SerialName("is_admin")
    val isAdmin: Boolean,
    @SerialName("can_upload")
    val canUpload: Boolean,
    @SerialName("can_edit")
    val canEdit: Boolean,
    @SerialName("can_download")
    val canDownload: Boolean,
    @SerialName("kindle_email")
    val kindleEmail: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("has_avatar")
    val hasAvatar: Boolean = false,
    @SerialName("hidden_formats")
    val hiddenFormats: List<String> = emptyList(),
    @SerialName("book_detail_scroll_stops")
    val bookDetailScrollStops: Boolean = false,
)