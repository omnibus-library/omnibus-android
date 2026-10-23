package com.omnibus.omnibus.data.auth

import android.util.Log
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthSignInRequestDto
import com.omnibus.omnibus.data.auth.remote.AuthSignInResponseDto
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val omnibusHttpClient: OmnibusHttpClient,
    private val authTokenStore: AuthTokenStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    // STUB(Stage 3): replace with the real implementation.
    override val isSignedIn: Flow<Boolean>
        get() = TODO("Stage 3")

    override suspend fun signIn(
        username: String,
        password: String
    ): AuthSignInResult {

        val body = AuthSignInRequestDto(username = username, password = password).toJsonRequest()

        return withContext(ioDispatcher) {
            val request = omnibusHttpClient.request(LOGIN_PATH)?.post(body)?.build()
                ?: return@withContext AuthSignInResult.NoServer
            val httpResponse = try {
                omnibusHttpClient.execute(request)
            } catch (ex: IOException) {
                Log.e(TAG, "Unable to reach the server", ex)
                return@withContext AuthSignInResult.Unreachable
            }

            httpResponse.use { result ->
                Log.d(TAG, "Login Result: $result")

                if (result.isSuccessful) {
                    try {
                        Log.d(TAG, "Login Status: Success")
                        val response = AuthSignInResponseDto.fromJsonString(result.body.string())
                        val token = response.token
                        if (token.isNullOrBlank()) {
                            AuthSignInResult.ServerError
                        } else {
                            authTokenStore.save(token)
                            AuthSignInResult.Success(authToken = token)
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // The exception message can include the response body, which carries the token.
                        Log.e(TAG, "Unable to complete sign-in")
                        AuthSignInResult.ServerError
                    }
                } else {
                    Log.d(TAG, "Login Status: Failed")
                    Log.d(TAG, "Failure Response: ${result.message}")
                    when (result.code) {
                        401, 403 -> AuthSignInResult.InvalidCredentials
                        429 -> AuthSignInResult.TooManyAttempts
                        else -> AuthSignInResult.ServerError
                    }
                }
            }
        }
    }

    override suspend fun signOut(): AuthSignOutResult =
        try {
            //TODO: Actually call the endpoint to clear the session, if there is one
            authTokenStore.clear()
            AuthSignOutResult.Success
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.e(TAG, "Unable to clear the stored auth token", ex)
            AuthSignOutResult.Failure
        }

    // STUB(Stage 3): replace with the real implementation.
    override suspend fun verifySession(): AuthSessionResult = TODO("Stage 3")

    internal companion object {
        const val TAG = "DefaultAuthRepository"

        const val LOGIN_PATH = "api/auth/login"
    }
}