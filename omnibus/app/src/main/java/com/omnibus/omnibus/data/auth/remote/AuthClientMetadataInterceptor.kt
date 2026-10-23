package com.omnibus.omnibus.data.auth.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import javax.inject.Inject

/** Adds [ClientInfo] fields to the JSON bodies of login and register requests. */
class AuthClientMetadataInterceptor @Inject constructor(
    private val clientInfo: ClientInfo,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val originalBody = request.body
        if (originalBody == null || !shouldAnnotate(request.method, request.url.encodedPath)) {
            return chain.proceed(request)
        }

        val buffer = Buffer()
        originalBody.writeTo(buffer)
        val originalJson = runCatching {
            Json.parseToJsonElement(buffer.readUtf8()).jsonObject
        }.getOrNull() ?: return chain.proceed(request)

        val annotated = JsonObject(
            originalJson + mapOf(
                "client_kind" to JsonPrimitive(CLIENT_KIND),
                "device_name" to JsonPrimitive(clientInfo.deviceName),
                "client_version" to JsonPrimitive(clientInfo.clientVersion),
            ),
        )
        val mediaType = originalBody.contentType() ?: JSON_MEDIA_TYPE
        val newBody = Json.encodeToString(JsonObject.serializer(), annotated)
            .toRequestBody(mediaType)
        return chain.proceed(request.newBuilder().post(newBody).build())
    }

    private companion object {
        const val CLIENT_KIND = "android"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun shouldAnnotate(method: String, encodedPath: String): Boolean =
            method == "POST" &&
                (encodedPath.endsWith("/api/auth/login") ||
                    encodedPath.endsWith("/api/auth/register"))
    }
}