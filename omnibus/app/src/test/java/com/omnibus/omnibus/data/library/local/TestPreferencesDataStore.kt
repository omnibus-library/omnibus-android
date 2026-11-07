package com.omnibus.omnibus.data.library.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** An in-memory [DataStore], holding [initial] until something is written. */
class TestPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** A [DataStore] whose every read and write fails with an [IOException]. */
class FailingPreferencesDataStore : DataStore<Preferences> {

    override val data: Flow<Preferences> = flow { throw IOException("boom") }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw IOException("boom")
}
