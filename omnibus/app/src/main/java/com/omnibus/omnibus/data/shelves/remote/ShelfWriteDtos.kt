package com.omnibus.omnibus.data.shelves.remote

import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfUpdate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The body of `PATCH api/shelves/{id}`. Null fields are left out of the JSON, so the server keeps them.
 */
@Serializable
internal data class UpdateShelfRequestDto(
    val name: String? = null,
    val description: String? = null,
    val visibility: String? = null,
    @SerialName("match_mode")
    val matchMode: String? = null,
    val rules: List<ShelfRuleDto>? = null,
) {
    fun toJsonRequest(): RequestBody = ShelvesJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    companion object {

        /** The body for [update]. Match mode and rules are sent only together, and rules the app can't encode are dropped. */
        fun of(update: ShelfUpdate): UpdateShelfRequestDto {
            val matchMode = update.matchMode
            return UpdateShelfRequestDto(
                name = update.name,
                description = update.description,
                visibility = update.visibility?.wireToken(),
                matchMode = matchMode?.wireToken(),
                rules = if (matchMode != null) update.rules?.mapNotNull { ShelfRuleDto.of(it) } else null,
            )
        }
    }
}

/** The body of `POST api/shelves/preview`. */
@Serializable
internal data class RulePreviewRequestDto(
    @SerialName("match_mode")
    val matchMode: String,
    val rules: List<ShelfRuleDto>,
) {
    fun toJsonRequest(): RequestBody = ShelvesJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    companion object {

        /** The body for previewing [rules] under [matchMode]. Rules the app can't encode are dropped. */
        fun of(matchMode: ShelfMatchMode, rules: List<ShelfRule>): RulePreviewRequestDto = RulePreviewRequestDto(
            matchMode = matchMode.wireToken(),
            rules = rules.mapNotNull { ShelfRuleDto.of(it) },
        )
    }
}

/** The response of `POST api/shelves/preview`. */
@Serializable
internal data class RulePreviewDto(
    val matched: Long = 0,
    val total: Long = 0,
)

private fun ShelfMatchMode.wireToken(): String = when (this) {
    ShelfMatchMode.All -> "all"
    ShelfMatchMode.Any -> "any"
}

private fun ShelfVisibility.wireToken(): String = when (this) {
    ShelfVisibility.Private -> "private"
    ShelfVisibility.Public -> "public"
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
