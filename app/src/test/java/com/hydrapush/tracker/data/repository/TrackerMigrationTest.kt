package com.hydrapush.tracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hydrapush.tracker.data.local.MIGRATION_1_2
import com.hydrapush.tracker.data.local.PullUpSetEntity
import com.hydrapush.tracker.data.local.TrackerDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class TrackerMigrationTest {
    @Test
    fun opensProductionSchemaAndPreservesLegacyPerfectDay() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        val day = "2026-11-01"
        context.deleteDatabase(name)
        try {
            val schema = JSONObject(File("schemas/${TrackerDatabase::class.java.name}/1.json").readText())
                .getJSONObject("database")
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    val indices = entity.optJSONArray("indices") ?: JSONArray()
                    for (indexIndex in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(indexIndex).getString("createSql")
                            .replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                db.execSQL("INSERT INTO daily_snapshots VALUES ('$day', 500, 10, 1, 1, 1, 1, 41, 100)")
                db.execSQL("INSERT INTO water_entries VALUES (11, '$day', 500, 200)")
                db.execSQL("INSERT INTO push_up_sets VALUES (21, '$day', 10, 300)")
                db.execSQL("INSERT INTO configuration_revisions VALUES (41, '$day', 500, 10, 1, 1, 90)")
                db.execSQL("INSERT INTO achievements VALUES ('first_perfect', 400, '$day')")
                db.version = 1
            }

            val db = Room.databaseBuilder(context, TrackerDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
            try {
                val dao = db.dao()
                val snapshot = dao.getAllSnapshots().single()
                val water = dao.getAllWater().single()
                val pushUp = dao.getAllPushUpSets().single()
                assertNull(snapshot.pullUpGoalReps)
                assertFalse(snapshot.pullUpsRequired)
                assertEquals(41L, snapshot.requirementsRevisionId)
                assertEquals(200L, water.createdAtEpochMs)
                assertEquals(300L, pushUp.createdAtEpochMs)
                assertTrue(DayView(snapshot, listOf(water), listOf(pushUp)).metrics.perfect)
                assertNull(dao.getAllRevisions().single().pullUpGoalReps)
                assertEquals(day, dao.getAllAchievements().single().sourceDay)
                assertTrue(dao.getAllPullUpSets().isEmpty())
                val nextDay = "2026-11-02"
                dao.insertSnapshot(snapshot.copy(day = nextDay, pullUpGoalReps = 10, pullUpsRequired = true))
                dao.insertPullUpSet(PullUpSetEntity(day = nextDay, reps = 3))
                assertEquals(3L, dao.getPullUpTotal(nextDay))
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
        Unit
    }
}
