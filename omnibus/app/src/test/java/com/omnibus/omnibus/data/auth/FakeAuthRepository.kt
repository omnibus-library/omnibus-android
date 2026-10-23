package com.omnibus.omnibus.data.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** In-memory [AuthRepository] for tests. */
class FakeAuthRepository : AuthRepository {

    /** Backs [isSignedIn]. Set it to simulate a token being saved or cleared. */
    val signedIn = MutableStateFlow(false)

    /** When set, [isSignedIn] emits nothing until this completes, to observe the unread state. */
    var isSignedInGate: CompletableDeferred<Unit>? = null

    /** Returned by [signIn]. */
    var signInResult: AuthSignInResult = AuthSignInResult.Success(authToken = "token")

    /** When set, [signIn] suspends until this completes, to observe pending state. */
    var signInGate: CompletableDeferred<Unit>? = null

    /** Every (username, password) pair passed to [signIn], in order. */
    val signInAttempts = mutableListOf<Pair<String, String>>()

    /** Returned by [signOut]. */
    var signOutResult: AuthSignOutResult = AuthSignOutResult.Success

    /** Returned by [verifySession]. */
    var verifySessionResult: AuthSessionResult = AuthSessionResult.Valid

    /** How many times [verifySession] has been called. */
    var verifySessionCalls = 0
        private set

    override val isSignedIn: Flow<Boolean> = flow {
        isSignedInGate?.await()
        signedIn.collect { emit(it) }
    }

    override suspend fun signIn(username: String, password: String): AuthSignInResult {
        signInAttempts += username to password
        signInGate?.await()
        return signInResult
    }

    override suspend fun signOut(): AuthSignOutResult = signOutResult

    override suspend fun verifySession(): AuthSessionResult {
        verifySessionCalls++
        return verifySessionResult
    }
}
