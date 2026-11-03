package com.omnibus.omnibus.data.household.remote

import com.omnibus.omnibus.data.household.HouseholdReader
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient JSON for the household routes: unknown keys are ignored and a wrong-typed value takes the default. */
internal val HouseholdJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** One element of `GET api/books/{uuid}/household`. `last_session_at` is Unix seconds. */
@Serializable
data class BookHouseholdReaderDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("has_avatar") val hasAvatar: Boolean = false,
    @SerialName("seconds") val seconds: Long = 0,
    @SerialName("sessions") val sessions: Long = 0,
    @SerialName("last_session_at") val lastSessionAt: Long? = null,
) {
    /** This reader as a domain reader, or null when the row has no id or no name to show. */
    fun toDomain(): HouseholdReader? {
        val userId = id ?: return null
        val displayName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return HouseholdReader(
            userId = userId,
            name = displayName,
            hasAvatar = hasAvatar,
            seconds = seconds,
            sessions = sessions,
            lastSessionAtMillis = lastSessionAt?.takeIf { it > 0 }?.times(1_000L),
        )
    }
}
