package com.omnibus.omnibus.data.auth

interface AuthRepository {

    suspend fun signIn(username: String, password: String): AuthSignInResult

    suspend fun signOut(): AuthSignOutResult
}

sealed interface AuthSignInResult {

    val authToken: String?

    data class Success(override val authToken: String) : AuthSignInResult
    data object Failure : AuthSignInResult {
        override val authToken = null
    }

    data object Error: AuthSignInResult {
        override val authToken = null
    }
}

sealed interface AuthSignOutResult {

    data object Success : AuthSignOutResult
    data object Failure : AuthSignOutResult

}