package com.omnibus.omnibus.data.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Whether a session token is stored. A storage read failure counts as signed out. */
    val isSignedIn: Flow<Boolean>

    suspend fun signIn(username: String, password: String): AuthSignInResult

    /** Creates an account on the saved server and saves the bearer token on success. */
    suspend fun register(username: String, password: String): AuthRegisterResult

    suspend fun signOut(): AuthSignOutResult

    /** Confirms the stored session with the server. */
    suspend fun verifySession(): AuthSessionResult
}

sealed interface AuthSignInResult {

    /** The server accepted the credentials and the returned [authToken] has been saved. */
    data class Success(val authToken: String) : AuthSignInResult

    /** The server rejected the username or password (401 or 403). */
    data object InvalidCredentials : AuthSignInResult

    /** The server is rate limiting sign-in attempts (429). */
    data object TooManyAttempts : AuthSignInResult

    /** The request failed before the server responded. */
    data object Unreachable : AuthSignInResult

    /** The server answered with an error, an unusable body, no token, or the token couldn't be saved. */
    data object ServerError : AuthSignInResult

    /** No valid server is saved, so there is nowhere to sign in. */
    data object NoServer : AuthSignInResult
}

/** The outcome of creating an account. */
sealed interface AuthRegisterResult {
    /** Account created and [authToken] saved. */
    data class Success(val authToken: String) : AuthRegisterResult

    /** 409. Nothing saved. */
    data object UsernameTaken : AuthRegisterResult

    /** 403. Registration is closed. Nothing saved. */
    data object RegistrationDisabled : AuthRegisterResult

    /** 400. Username or password rejected. Nothing saved. */
    data object InvalidDetails : AuthRegisterResult

    /** 429. Nothing saved. */
    data object TooManyAttempts : AuthRegisterResult

    /** Transport failure before a response. Nothing saved. */
    data object Unreachable : AuthRegisterResult

    /** Other non-2xx, unusable body, blank token, or token save failed. */
    data object ServerError : AuthRegisterResult

    /** No saved server. No request. */
    data object NoServer : AuthRegisterResult
}

sealed interface AuthSignOutResult {

    data object Success : AuthSignOutResult
    data object Failure : AuthSignOutResult

}

/** The outcome of confirming the stored session with the server. */
sealed interface AuthSessionResult {

    /** The server accepted the session (2xx). */
    data object Valid : AuthSessionResult

    /** The server rejected the session (401), and the stored token has been cleared. */
    data object Rejected : AuthSessionResult

    /** The request failed before the server responded. */
    data object Unreachable : AuthSessionResult

    /** The server answered with any other non-2xx status. */
    data object ServerError : AuthSessionResult

    /** No valid server is saved, so there is nothing to verify against. */
    data object NoServer : AuthSessionResult

    /** No token is stored, so no request was made. */
    data object NoSession : AuthSessionResult
}
