package com.omnibus.omnibus.data.auth

import android.se.omapi.Session
import android.util.Log
import com.omnibus.omnibus.data.auth.di.IoDispatcher
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthSignInRequestDto
import com.omnibus.omnibus.data.auth.remote.AuthSignInResponseDto
import com.omnibus.omnibus.data.auth.remote.OmnibusHttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import javax.inject.Inject

interface AuthRepository {
    suspend fun login(username: String, password: String): AuthLoginResult
}

class DefaultAuthRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val authTokenStore: AuthTokenStore,
) : AuthRepository {

//    private val _sessionToken: MutableStateFlow<String?> = MutableStateFlow(null)
//    val sessionToken: StateFlow<String?> = _sessionToken.asStateFlow()

    /**
     * Performs a basic login using the provided username and password. Once authenticated, the
     * bearer token will be saved off for reference in subsequent requests.
     *
     * @param username [String] username provided by the user.
     * @param password [String] password provided by the user.
     *
     * @return [AuthLoginResult] auth determination of either Authorized or Unauthorized.
     */
    override suspend fun login(
        username: String,
        password: String
    ): AuthLoginResult {
        return withContext(ioDispatcher) {

            val requestBody = AuthSignInRequestDto(username, password).toJsonRequest()
            val request = omnibusHttpClient.request(LOGIN_PATH).post(requestBody).build()

            try {
                omnibusHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val token =
                            AuthSignInResponseDto.fromJsonString(response.body.string()).token
                        //todo: do something with the user info that returned from this call
                        authTokenStore.save(token)
                        AuthLoginResult.Authenticated
                    } else {
                        AuthLoginResult.Unauthenticated
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, ex.message ?: "Unknown Error")
                AuthLoginResult.Error
            }
        }
    }

    companion object {
        const val TAG = "AuthRepository"
        const val LOGIN_PATH = "/api/auth/login"
    }
}

sealed interface AuthLoginResult {
    data object Authenticated : AuthLoginResult
    data object Unauthenticated : AuthLoginResult
    data object Error : AuthLoginResult
}