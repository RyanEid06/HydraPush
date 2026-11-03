package com.hydrapush.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DailySnapshotEntity::class,
        WaterEntryEntity::class,
        PushUpSetEntity::class,
        ConfigurationRevisionEntity::class,
        AchievementEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao
}
