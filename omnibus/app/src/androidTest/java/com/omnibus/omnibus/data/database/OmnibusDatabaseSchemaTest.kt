package com.omnibus.omnibus.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OmnibusDatabaseSchemaTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, OmnibusDatabase::class.java)

    @After
    fun deleteDatabase() {
        instrumentation.targetContext.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun issue379_tc1_matchesTheExportedVersion1Schema() = runBlocking {
        // The exported version 1 schema, written as Room creates it, then opened by the current code.
        helper.createDatabase(DATABASE_NAME, 1).close()
        val database = Room.databaseBuilder(instrumentation.targetContext, OmnibusDatabase::class.java, DATABASE_NAME)
            .build()

        val tables = mutableListOf<String>()
        database.openHelper.readableDatabase.query(
            """
            SELECT name FROM sqlite_master WHERE type = 'table'
                AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table')
            """,
        ).use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        database.close()

        assertEquals(EXPECTED_TABLES, tables.sorted())
    }

    private companion object {
        const val DATABASE_NAME = "schema-test.db"

        val EXPECTED_TABLES = listOf(
            "books",
            "dismissed_resume_offers",
            "libraries",
            "listening_positions",
            "listening_sessions",
            "playback_rates",
            "reading_positions",
        )
    }
}
