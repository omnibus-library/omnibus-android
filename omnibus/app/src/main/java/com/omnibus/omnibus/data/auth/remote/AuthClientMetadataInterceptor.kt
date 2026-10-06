package com.omnibus.omnibus.data.auth.remote

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
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

class AuthClientMetadataInterceptor @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
                "device_name" to JsonPrimitive(deviceName()),
                "client_version" to JsonPrimitive(clientVersion()),
            ),
        )
        val mediaType = originalBody.contentType() ?: JSON_MEDIA_TYPE
        val newBody = Json.encodeToString(JsonObject.serializer(), annotated)
            .toRequestBody(mediaType)
        return chain.proceed(request.newBuilder().post(newBody).build())
    }

    private fun deviceName(): String = Build.MODEL.ifBlank { "Android" }

    private fun clientVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"

    private companion object {
        const val CLIENT_KIND = "android"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun shouldAnnotate(method: String, encodedPath: String): Boolean =
            method == "POST" &&
                (encodedPath.endsWith("/api/auth/login") ||
                    encodedPath.endsWith("/api/auth/register"))
    }
}