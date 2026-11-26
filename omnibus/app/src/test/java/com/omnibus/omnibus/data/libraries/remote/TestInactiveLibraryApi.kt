package com.omnibus.omnibus.data.libraries.remote

import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl

/** An [InactiveLibraryApi] that records every call and answers as the test sets. */
class TestInactiveLibraryApi : InactiveLibraryApi {

    /** One call, with the server and token it was made with. */
    sealed interface Call {
        val baseUrl: HttpUrl
        val token: String
    }

    data class Logout(override val baseUrl: HttpUrl, override val token: String) : Call

    data class SendPosition(
        override val baseUrl: HttpUrl,
        override val token: String,
        val position: AudioPosition,
    ) : Call

    data class SendSessions(
        override val baseUrl: HttpUrl,
        override val token: String,
        val batch: List<ListeningSessionReport>,
    ) : Call

    data class PutRate(
        override val baseUrl: HttpUrl,
        override val token: String,
        val bookUuid: String,
        val rate: Double,
    ) : Call

    /** Every call, in call order. */
    val calls = mutableListOf<Call>()

    /** What a position send answers. It may throw. */
    var positionAnswer: (AudioPosition) -> Boolean = { true }

    /** What a session batch send answers. It may throw. */
    var sessionsAnswer: (List<ListeningSessionReport>) -> Boolean = { true }

    /** What a rate write answers. It may throw. */
    var rateAnswer: (String, Double) -> Boolean = { _, _ -> true }

    /** When set, every send waits for it before answering. */
    var sendGate: CompletableDeferred<Unit>? = null

    override suspend fun logout(baseUrl: HttpUrl, token: String) {
        calls += Logout(baseUrl, token)
    }

    override suspend fun sendPosition(baseUrl: HttpUrl, token: String, position: AudioPosition): Boolean {
        calls += SendPosition(baseUrl, token, position)
        sendGate?.await()
        return positionAnswer(position)
    }

    override suspend fun sendSessions(
        baseUrl: HttpUrl,
        token: String,
        batch: List<ListeningSessionReport>,
    ): Boolean {
        calls += SendSessions(baseUrl, token, batch)
        sendGate?.await()
        return sessionsAnswer(batch)
    }

    override suspend fun putRate(baseUrl: HttpUrl, token: String, bookUuid: String, rate: Double): Boolean {
        calls += PutRate(baseUrl, token, bookUuid, rate)
        sendGate?.await()
        return rateAnswer(bookUuid, rate)
    }
}
