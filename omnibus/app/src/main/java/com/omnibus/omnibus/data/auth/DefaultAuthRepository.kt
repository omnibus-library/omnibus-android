package com.omnibus.omnibus.data.auth

import android.util.Log
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthSignInRequestDto
import com.omnibus.omnibus.data.auth.remote.AuthSignInResponseDto
import com.omnibus.omnibus.data.auth.remote.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val omnibusHttpClient: OmnibusHttpClient,
    private val authTokenStore: AuthTokenStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override suspend fun signIn(
        username: String,
        password: String
    ): AuthSignInResult {

        val body = AuthSignInRequestDto(username = username, password = password).toJsonRequest()
        val request = omnibusHttpClient.request(LOGIN_PATH).post(body).build()

        return withContext(ioDispatcher) {
            val call = omnibusHttpClient.newCall(request)
            val httpResponse = try {
                call.execute()
            } catch (ex: IOException) {
                Log.e(TAG, "Unable to reach the server", ex)
                return@withContext AuthSignInResult.Error
            }

            httpResponse.use { result ->
                Log.d(TAG, "Login Result: $result")

                if (result.isSuccessful) {
                    try {
                        Log.d(TAG, "Login Status: Success")
                        val response = AuthSignInResponseDto.fromJsonString(result.body.string())
                        response.token?.let { token ->
                            authTokenStore.save(token)
                            AuthSignInResult.Success(authToken = token)
                        } ?: AuthSignInResult.Error
                    } catch (ex: Exception) {
                        Log.e(TAG, ex.message ?: "Error message not available")
                        AuthSignInResult.Error
                    }
                } else {
                    Log.d(TAG, "Login Status: Failed")
                    Log.d(TAG, "Failure Response: ${result.message}")
                    AuthSignInResult.Failure
                }
            }
        }
    }

    override suspend fun signOut(): AuthSignOutResult =
        try {
            //TODO: Actually call the endpoint to clear the session, if there is one
            authTokenStore.clear()
            AuthSignOutResult.Success
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to clear the stored auth token", exception)
            AuthSignOutResult.Failure
        }

    private companion object {
        const val TAG = "DefaultAuthRepository"

        const val LOGIN_PATH = "/api/auth/login"
    }
}