package com.omnibus.omnibus.data.libraryconfiguration.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Body of `GET /api/_health`.
 *
 * @property app application identifier; `"omnibus"` for an Omnibus server.
 * @property status liveness status; `"ok"` when the server is healthy.
 * @property buildId digits-only fingerprint of the server process, changes on every restart.
 * @property version server release version, or `"dev"` for unversioned builds.
 * @property time server clock in unix seconds, for measuring the device's clock offset.
 * @property repoRoot server working directory; only meaningful for local development servers.
 */
@Serializable
data class ServerValidationResponseDto(
    val app: String,
    val status: String,
    @SerialName("build_id")
    val buildId: String? = null,
    val version: String? = null,
    val time: Long? = null,
    @SerialName("repo_root")
    val repoRoot: String? = null,
) {
    /** Whether the responder identifies as a healthy Omnibus server rather than some other HTTP service. */
    val isHealthyOmnibusServer: Boolean
        get() = app == OMNIBUS_APP && status == STATUS_OK

    companion object {
        private const val OMNIBUS_APP = "omnibus"
        private const val STATUS_OK = "ok"

        // Lenient so a server adding fields to the health payload doesn't fail validation.
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * @throws kotlinx.serialization.SerializationException if [jsonString] isn't a health payload.
         */
        fun fromJsonString(jsonString: String): ServerValidationResponseDto =
            json.decodeFromString<ServerValidationResponseDto>(jsonString)
    }
}
