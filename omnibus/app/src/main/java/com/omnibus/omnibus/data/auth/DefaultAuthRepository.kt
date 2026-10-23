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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val omnibusHttpClient: OmnibusHttpClient,
    private val authTokenStore: AuthTokenStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override val isSignedIn: Flow<Boolean> =
        authTokenStore.token
            .map { !it.isNullOrBlank() }
            .catch { emit(false) }
            .distinctUntilChanged()

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

    @Suppress("UNUSED_PARAMETER")
    override suspend fun register(username: String, password: String): AuthRegisterResult {
        throw UnsupportedOperationException("register is not implemented")
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

    override suspend fun verifySession(): AuthSessionResult = withContext(ioDispatcher) {
        val storedToken = try {
            authTokenStore.token.first()
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to read the stored auth token", ex)
            null
        }
        if (storedToken.isNullOrBlank()) {
            return@withContext AuthSessionResult.NoSession
        }

        val request = omnibusHttpClient.request(ME_PATH)
            ?: return@withContext AuthSessionResult.NoServer

        val httpResponse = try {
            omnibusHttpClient.execute(request.get().build())
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to reach the server", ex)
            return@withContext AuthSessionResult.Unreachable
        }

        httpResponse.use { result ->
            Log.d(TAG, "Verify session result: $result")
            when {
                result.isSuccessful -> {
                    Log.d(TAG, "Session status: Valid")
                    AuthSessionResult.Valid
                }
                // The client already invalidated the token on this 401.
                result.code == 401 -> {
                    Log.d(TAG, "Session status: Rejected")
                    AuthSessionResult.Rejected
                }
                else -> {
                    Log.d(TAG, "Session status: Failed")
                    Log.d(TAG, "Failure Response: ${result.message}")
                    AuthSessionResult.ServerError
                }
            }
        }
    }

    internal companion object {
        const val TAG = "DefaultAuthRepository"

        const val LOGIN_PATH = "api/auth/login"
        const val ME_PATH = "api/auth/me"
    }
}
