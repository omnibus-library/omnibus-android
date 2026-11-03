package com.omnibus.omnibus.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject

/** Mints the client id for a created annotation. The server dedupes creates on it, so a retry never makes a second row. */
fun interface ClientIdSource {
    /** A new id, unique per call. */
    fun mint(): String
}

/** A random UUID string, 36 characters, which is within the server's 64-character limit. */
class UuidClientIdSource @Inject constructor() : ClientIdSource {
    override fun mint(): String = UUID.randomUUID().toString()
}

/** Binds [UuidClientIdSource] as the app-wide [ClientIdSource]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ClientIdSourceModule {

    @Binds
    abstract fun bindClientIdSource(source: UuidClientIdSource): ClientIdSource
}
