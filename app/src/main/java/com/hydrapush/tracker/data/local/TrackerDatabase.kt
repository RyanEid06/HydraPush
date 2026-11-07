package com.hydrapush.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_snapshots ADD COLUMN pullUpGoalReps INTEGER")
        db.execSQL("ALTER TABLE daily_snapshots ADD COLUMN pullUpsRequired INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE configuration_revisions ADD COLUMN pullUpGoalReps INTEGER")
        db.execSQL(
            """CREATE TABLE pull_up_sets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                day TEXT NOT NULL,
                reps INTEGER NOT NULL,
                createdAtEpochMs INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX index_pull_up_sets_day ON pull_up_sets (day)")
    }
}

@Database(
    entities = [
        DailySnapshotEntity::class,
        WaterEntryEntity::class,
        PushUpSetEntity::class,
        PullUpSetEntity::class,
        ConfigurationRevisionEntity::class,
        AchievementEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao
}
