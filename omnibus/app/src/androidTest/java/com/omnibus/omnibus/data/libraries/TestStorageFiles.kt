package com.omnibus.omnibus.data.libraries

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.tink.AeadSerializer
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.omnibus.omnibus.data.database.OmnibusDatabase
import com.omnibus.omnibus.data.libraries.local.DefaultLibraryTokenStore
import com.omnibus.omnibus.data.libraries.local.LegacyTokenAdopter
import com.omnibus.omnibus.data.libraries.local.LibraryCovers
import com.omnibus.omnibus.data.libraries.upgrade.LegacyFiles
import com.omnibus.omnibus.data.libraries.upgrade.StorageUpgrade
import com.omnibus.omnibus.di.TimeSource
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job

/**
 * One test's files: a fresh `filesDir` with its `datastore/` legacy directory, its covers and its token store.
 * [delete] removes them.
 */
class TestStorageFiles {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    val filesDir: File = File(context.cacheDir, "libraries-${UUID.randomUUID()}").apply { mkdirs() }
    val legacyDir: File = File(filesDir, "datastore").apply { mkdirs() }
    val covers = LibraryCovers(filesDir)
    val tokens = DefaultLibraryTokenStore(context, Dispatchers.IO) { File(legacyDir, TOKEN_FILE) }

    fun upgrade(
        database: OmnibusDatabase,
        adopter: LegacyTokenAdopter = tokens,
        beforeImportCommit: suspend () -> Unit = {},
    ): StorageUpgrade = StorageUpgrade(
        database = database,
        tokenAdopter = adopter,
        covers = covers,
        legacyDir = legacyDir,
        ioDispatcher = Dispatchers.IO,
        clock = TimeSource { NOW },
        beforeImportCommit = beforeImportCommit,
    )

    /** A repository over [database]. Pass [active] when the test reads the active id through the same instance. */
    fun librariesRepository(
        database: OmnibusDatabase,
        upgrade: StorageUpgrade,
        active: DefaultActiveLibrary = DefaultActiveLibrary(database.librariesDao(), upgrade),
    ): DefaultLibrariesRepository {
        return DefaultLibrariesRepository(
            ioDispatcher = Dispatchers.IO,
            dao = database.librariesDao(),
            activeLibrary = active,
            tokens = tokens,
            covers = covers,
            upgrade = upgrade,
            clock = TimeSource { NOW },
        )
    }

    fun delete() {
        filesDir.deleteRecursively()
    }

    companion object {
        const val TOKEN_FILE = "auth_token.preferences_pb"
        const val NOW = 1_000L
    }
}

/** Writes the legacy DataStore file [name] under the legacy directory, then stops its DataStore so it can be read. */
suspend fun TestStorageFiles.writeLegacy(name: String, edit: MutablePreferences.() -> Unit) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(legacyDir, name) })
            .updateData { preferences -> preferences.toMutablePreferences().apply(edit) }
    } finally {
        scope.coroutineContext.job.cancelAndJoin()
    }
}

/**
 * Writes [token] the way the builds before libraries did: under `auth_token`, in the encrypted token file. The keyset,
 * master key and associated data are written out literally, not taken from the code under test, so a renamed
 * constant fails the test.
 */
suspend fun TestStorageFiles.writeLegacyToken(token: String) {
    AeadConfig.register()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context, "auth_token_keyset", "omnibus_auth_keyset")
        .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
        .withMasterKeyUri("android-keystore://omnibus_auth_master_key")
        .build()
        .keysetHandle
    val serializer: Serializer<Preferences> = AeadSerializer(
        aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java),
        wrappedSerializer = PreferencesFileSerializer,
        associatedData = "auth_token.preferences_pb".encodeToByteArray(),
    )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        DataStoreFactory.create(serializer = serializer, scope = scope, produceFile = { File(legacyDir, TestStorageFiles.TOKEN_FILE) })
            .updateData { preferences ->
                preferences.toMutablePreferences().apply { this[stringPreferencesKey("auth_token")] = token }
            }
    } finally {
        scope.coroutineContext.job.cancelAndJoin()
    }
}

/**
 * Writes a typical install from before libraries: a saved server with a port, two reading positions, a cached and
 * pending position, three queued sessions, a rate, a dismissed offer, a legacy token and loose cover files.
 */
suspend fun TestStorageFiles.writeTypicalLegacyInstall(address: String = "example.com:8080/") {
    writeLegacy(LegacyFiles.SERVER_CONFIGURATION) {
        this[stringPreferencesKey("server_address")] = address
    }
    writeLegacy(LegacyFiles.READING_POSITIONS) {
        this[stringPreferencesKey("position.book-1")] = """{"cfi":"epubcfi(/6/2)","percent":10,"clientUpdatedAt":1}"""
        this[stringPreferencesKey("position.book-2")] = """{"percent":20,"clientUpdatedAt":2}"""
    }
    writeLegacy(LegacyFiles.AUDIO_POSITIONS) {
        val position = """{"positionSeconds":30.0,"bookFileId":5,"clientUpdatedAt":3}"""
        this[stringPreferencesKey("audio.cached.book-1")] = position
        this[stringPreferencesKey("audio.pending.book-1")] = position
        this[stringPreferencesKey("audio.sessions")] = legacySessions(
            "book-1" to "session-1",
            "book-1" to "session-2",
            "book-2" to "session-3",
        )
    }
    writeLegacy(LegacyFiles.PLAYBACK_RATES) {
        this[doublePreferencesKey("cached:book-1")] = 1.5
        this[doublePreferencesKey("pending:book-1")] = 1.5
    }
    writeLegacy(LegacyFiles.RESUME_OFFERS) {
        this[longPreferencesKey("dismissed_clock:book-2:audio")] = 42L
    }
    writeLegacyToken("legacy-token")
    covers.root.mkdirs()
    File(covers.root, "journal").writeText("journal")
    File(covers.root, "entry-1").writeText("cover-1")
}

/** The old session queue JSON for [sessions], given as book uuid and client id pairs. */
private fun legacySessions(vararg sessions: Pair<String, String>): String =
    sessions.joinToString(separator = ",", prefix = "[", postfix = "]") { (bookUuid, clientId) ->
        "{\"bookUuid\":\"$bookUuid\",\"startedAt\":1,\"endedAt\":2,\"progressUnits\":3," +
            "\"clientId\":\"$clientId\",\"utcOffsetMinutes\":0,\"timeZone\":\"UTC\"}"
    }
