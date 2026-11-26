package com.omnibus.omnibus.data.auth

import android.util.Log
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthSignInRequestDto
import com.omnibus.omnibus.data.auth.remote.AuthSignInResponseDto
import com.omnibus.omnibus.data.auth.remote.UserInfoDto
import com.omnibus.omnibus.data.configuration.ServerAddress
import com.omnibus.omnibus.data.libraries.ActiveLibrary
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.IoDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class DefaultAuthRepository @Inject constructor(
    private val omnibusHttpClient: OmnibusHttpClient,
    private val authTokenStore: AuthTokenStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val libraries: LibrariesRepository,
    private val activeLibrary: ActiveLibrary,
    // #371
    private val libraryTokens: LibraryTokenStore,
) : AuthRepository {

    override val isSignedIn: Flow<Boolean> =
        authTokenStore.token
            .map { !it.isNullOrBlank() }
            .catch { emit(false) }
            .distinctUntilChanged()

    /** The user the server last reported. Only exposed while signed in, so a stale user never outlives the token. */
    private val lastUser = MutableStateFlow<CurrentUser?>(null)

    override val currentUser: Flow<CurrentUser?> =
        combine(lastUser, isSignedIn) { user, signedIn -> user.takeIf { signedIn } }
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
                            lastUser.value = response.user.toCurrentUser()
                            recordUsername(response.user.username)
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

    override suspend fun register(
        username: String,
        password: String
    ): AuthRegisterResult {

        val body = AuthSignInRequestDto(username = username, password = password).toJsonRequest()

        return withContext(ioDispatcher) {
            val request = omnibusHttpClient.request(REGISTER_PATH)?.post(body)?.build()
                ?: return@withContext AuthRegisterResult.NoServer
            val httpResponse = try {
                omnibusHttpClient.execute(request)
            } catch (ex: IOException) {
                Log.e(TAG, "Unable to reach the server", ex)
                return@withContext AuthRegisterResult.Unreachable
            }

            httpResponse.use { result ->
                Log.d(TAG, "Register Result: $result")

                if (result.isSuccessful) {
                    try {
                        Log.d(TAG, "Register Status: Success")
                        val response = AuthSignInResponseDto.fromJsonString(result.body.string())
                        val token = response.token
                        if (token.isNullOrBlank()) {
                            AuthRegisterResult.ServerError
                        } else {
                            authTokenStore.save(token)
                            lastUser.value = response.user.toCurrentUser()
                            recordUsername(response.user.username)
                            AuthRegisterResult.Success(authToken = token)
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // The exception message can include the response body, which carries the token.
                        Log.e(TAG, "Unable to complete registration")
                        AuthRegisterResult.ServerError
                    }
                } else {
                    Log.d(TAG, "Register Status: Failed")
                    Log.d(TAG, "Failure Response: ${result.message}")
                    when (result.code) {
                        409 -> AuthRegisterResult.UsernameTaken
                        403 -> AuthRegisterResult.RegistrationDisabled
                        400 -> AuthRegisterResult.InvalidDetails
                        429 -> AuthRegisterResult.TooManyAttempts
                        else -> AuthRegisterResult.ServerError
                    }
                }
            }
        }
    }

    override suspend fun signOut(): AuthSignOutResult =
        try {
            //TODO: Actually call the endpoint to clear the session, if there is one
            authTokenStore.clear()
            lastUser.value = null
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
                    // A body that doesn't decode keeps the user from the last good check.
                    readUser(result)?.let { user ->
                        lastUser.value = user.toCurrentUser()
                        recordUsername(user.username)
                    }
                    AuthSessionResult.Valid
                }
                // The client already invalidated the token on this 401.
                result.code == 401 -> {
                    Log.d(TAG, "Session status: Rejected")
                    lastUser.value = null
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

    /**
     * The user in a 2xx `/me` body, or null when it can't be read. `/me` returns the user object
     * directly, with keys [UserInfoDto] doesn't declare, so this decoder ignores unknown keys.
     */
    private fun readUser(result: Response): UserInfoDto? =
        try {
            MeJson.decodeFromString<UserInfoDto>(result.body.string())
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.w(TAG, "Unable to read the current user from the session check", ex)
            null
        }

    // #366
    /**
     * Stores [username] on the active library. A blank name, no active library or a storage failure stores
     * nothing, and nothing but a cancellation is thrown, so the caller's result never changes.
     */
    private suspend fun recordUsername(username: String) {
        if (username.isBlank()) return
        try {
            val libraryId = activeLibrary.id() ?: return
            libraries.setUsername(libraryId, username)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.e(TAG, "Unable to record the username", ex)
        }
    }

    // #371
    override suspend fun signInToNewLibrary(
        address: String,
        username: String,
        password: String,
        createAccount: Boolean,
    ): AuthNewLibraryResult = withContext(ioDispatcher) {
        val normalized = ServerAddress.normalize(address) ?: return@withContext AuthNewLibraryResult.ServerError
        // Never two libraries for one server, even if the Connect check was skipped.
        if (libraries.findByAddress(normalized) != null) return@withContext AuthNewLibraryResult.AlreadySaved
        val body = AuthSignInRequestDto(username = username, password = password).toJsonRequest()
        // The explicit-base-URL builder never adds Authorization, so a 401 here can't clear the active token.
        val request = omnibusHttpClient
            .request(normalized.toHttpUrl(), if (createAccount) REGISTER_PATH else LOGIN_PATH)
            .post(body)
            .build()
        val httpResponse = try {
            omnibusHttpClient.execute(request)
        } catch (ex: IOException) {
            Log.e(TAG, "Unable to reach the new library's server", ex)
            return@withContext AuthNewLibraryResult.Unreachable
        }

        httpResponse.use { result ->
            Log.d(TAG, "New library sign-in result: $result")
            if (!result.isSuccessful) {
                return@use newLibraryFailure(result.code, createAccount)
            }
            val response = try {
                AuthSignInResponseDto.fromJsonString(result.body.string())
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // The exception message can include the response body, which carries the token.
                Log.e(TAG, "Unable to read the new library's sign-in")
                return@use AuthNewLibraryResult.ServerError
            }
            val token = response.token
            if (token.isNullOrBlank()) return@use AuthNewLibraryResult.ServerError
            // Not cancellable: leaving the screen must not strand a library without its token.
            withContext(NonCancellable) { saveNewLibrary(normalized, token, response.user.username) }
        }
    }

    /** Adds [address] as an inactive library and stores its [token] under it. Removes the row if the save fails. */
    private suspend fun saveNewLibrary(address: String, token: String, username: String): AuthNewLibraryResult {
        val library = libraries.add(address, activate = false) ?: return AuthNewLibraryResult.ServerError
        try {
            libraryTokens.save(library.id, token)
        } catch (ex: Exception) {
            // Don't log the exception: it may carry the token.
            Log.e(TAG, "Unable to store the new library's token")
            libraries.remove(library.id)
            return AuthNewLibraryResult.ServerError
        }
        if (username.isNotBlank()) {
            try {
                libraries.setUsername(library.id, username)
            } catch (ex: Exception) {
                Log.e(TAG, "Unable to record the username", ex)
            }
        }
        return AuthNewLibraryResult.Success(libraryId = library.id)
    }

    private fun newLibraryFailure(code: Int, createAccount: Boolean): AuthNewLibraryResult =
        if (createAccount) {
            when (code) {
                409 -> AuthNewLibraryResult.UsernameTaken
                403 -> AuthNewLibraryResult.RegistrationDisabled
                400 -> AuthNewLibraryResult.InvalidDetails
                429 -> AuthNewLibraryResult.TooManyAttempts
                else -> AuthNewLibraryResult.ServerError
            }
        } else {
            when (code) {
                401, 403 -> AuthNewLibraryResult.InvalidCredentials
                429 -> AuthNewLibraryResult.TooManyAttempts
                else -> AuthNewLibraryResult.ServerError
            }
        }

    internal companion object {
        const val TAG = "DefaultAuthRepository"

        const val LOGIN_PATH = "api/auth/login"
        const val REGISTER_PATH = "api/auth/register"
        const val ME_PATH = "api/auth/me"

        private val MeJson = Json { ignoreUnknownKeys = true }
    }
}
