package com.omnibus.omnibus.data.auth.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.omnibus.omnibus.data.auth.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Persists the bearer token used to authenticate requests against the Omnibus API.
 */
interface AuthTokenStore {
    /** The currently stored token, or `null` when signed out. */
    val token: Flow<String?>

    suspend fun save(token: String)

    suspend fun clear()
}

@Singleton
class DefaultAuthTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthTokenStore {
    private val dataStore: DataStore<Preferences> by lazy {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFERENCES_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        val serializer = AeadSerializer(
            aead = keysetHandle.getPrimitive(
                RegistryConfiguration.get(),
                Aead::class.java,
            ),
            wrappedSerializer = PreferencesFileSerializer,
            associatedData = DATASTORE_FILE_NAME.encodeToByteArray(),
        )

        DataStoreFactory.create(
            serializer = serializer,
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { context.dataStoreFile(DATASTORE_FILE_NAME) },
        )
    }

    override val token: Flow<String?> = flow {
        emitAll(dataStore.data.map { preferences ->
            preferences[AUTH_TOKEN]
        })
    }

    override suspend fun save(token: String) {
        withContext(ioDispatcher) {
            dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    this[AUTH_TOKEN] = token
                }
            }
        }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    remove(AUTH_TOKEN)
                }
            }
        }
    }

    private companion object {
        const val DATASTORE_FILE_NAME = "auth_token.preferences_pb"
        const val KEYSET_NAME = "auth_token_keyset"
        const val KEYSET_PREFERENCES_FILE = "omnibus_auth_keyset"
        const val MASTER_KEY_URI = "android-keystore://omnibus_auth_master_key"
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
    }
}
