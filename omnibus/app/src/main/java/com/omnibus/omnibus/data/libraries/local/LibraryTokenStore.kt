package com.omnibus.omnibus.data.libraries.local

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
import com.omnibus.omnibus.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

/** Keeps the auth token of each library (C3). */
interface LibraryTokenStore {

    /** The token of [libraryId], or null when it is signed out there. */
    fun getTokenFlow(libraryId: String): Flow<String?>

    /** Stores [token] for [libraryId], replacing its previous token. */
    suspend fun save(libraryId: String, token: String)

    /** Removes the token of [libraryId]. Other libraries keep theirs. */
    suspend fun clear(libraryId: String)
}

/** Moves the token of a build before libraries into the library that the upgrade creates. Used only by the upgrade. */
interface LegacyTokenAdopter {

    /**
     * Moves the legacy token to [libraryId], keeping a token already stored for it, and removes the legacy
     * token either way. With a null [libraryId] it only removes the legacy token.
     */
    suspend fun adoptLegacyToken(libraryId: String?)
}

/**
 * The tokens of every library, in the encrypted `auth_token.preferences_pb`, one key per library. This is
 * the only DataStore on that file in the process, so it is a singleton bound to both interfaces.
 *
 * The encryption setup (keyset, master key and associated data) is the one used since the first release. Changing
 * any of it signs every reader out.
 */
@Singleton
class DefaultLibraryTokenStore internal constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val file: () -> File,
) : LibraryTokenStore, LegacyTokenAdopter {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context, ioDispatcher, { context.dataStoreFile(FILE_NAME) })

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
            associatedData = FILE_NAME.encodeToByteArray(),
        )

        DataStoreFactory.create(
            serializer = serializer,
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = file,
        )
    }

    override fun getTokenFlow(libraryId: String): Flow<String?> {
        val key = tokenKey(libraryId)
        return flow {
            emitAll(dataStore.data.map { preferences -> preferences[key] })
        }
    }

    override suspend fun save(libraryId: String, token: String) {
        val key = tokenKey(libraryId)
        withContext(ioDispatcher) {
            dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    this[key] = token
                }
            }
        }
    }

    override suspend fun clear(libraryId: String) {
        val key = tokenKey(libraryId)
        withContext(ioDispatcher) {
            dataStore.updateData { preferences ->
                preferences.toMutablePreferences().apply {
                    remove(key)
                }
            }
        }
    }

    override suspend fun adoptLegacyToken(libraryId: String?) {
        withContext(ioDispatcher) {
            dataStore.updateData { preferences ->
                val legacy = preferences[LEGACY_TOKEN]
                preferences.toMutablePreferences().apply {
                    remove(LEGACY_TOKEN)
                    if (libraryId != null && legacy != null && this[tokenKey(libraryId)] == null) {
                        this[tokenKey(libraryId)] = legacy
                    }
                }
            }
        }
    }

    private companion object {
        const val FILE_NAME = "auth_token.preferences_pb"
        const val KEYSET_NAME = "auth_token_keyset"
        const val KEYSET_PREFERENCES_FILE = "omnibus_auth_keyset"
        const val MASTER_KEY_URI = "android-keystore://omnibus_auth_master_key"

        /** The token key of the builds before libraries. */
        val LEGACY_TOKEN = stringPreferencesKey("auth_token")

        fun tokenKey(libraryId: String) = stringPreferencesKey("auth_token.$libraryId")
    }
}
