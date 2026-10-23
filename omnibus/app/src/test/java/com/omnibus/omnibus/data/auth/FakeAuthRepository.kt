package com.omnibus.omnibus.data.auth

import kotlinx.coroutines.CompletableDeferred

/** In-memory [AuthRepository] for tests. */
class FakeAuthRepository : AuthRepository {

    /** Returned by [signIn]. */
    var signInResult: AuthSignInResult = AuthSignInResult.Success(authToken = "token")

    /** When set, [signIn] suspends until this completes, to observe pending state. */
    var signInGate: CompletableDeferred<Unit>? = null

    /** Every (username, password) pair passed to [signIn], in order. */
    val signInAttempts = mutableListOf<Pair<String, String>>()

    /** Returned by [signOut]. */
    var signOutResult: AuthSignOutResult = AuthSignOutResult.Success

    override suspend fun signIn(username: String, password: String): AuthSignInResult {
        signInAttempts += username to password
        signInGate?.await()
        return signInResult
    }

    override suspend fun signOut(): AuthSignOutResult = signOutResult
}
