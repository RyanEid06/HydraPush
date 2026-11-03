package com.hydrapush.tracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "daily_snapshots")
data class DailySnapshotEntity(
    @PrimaryKey val day: String, // ISO date format YYYY-MM-DD
    val waterGoalMl: Int,
    val pushUpGoalReps: Int,
    val creatineRequired: Boolean,
    val proteinRequired: Boolean,
    val creatineDone: Boolean = false,
    val proteinDone: Boolean = false,
    val requirementsRevisionId: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "water_entries", indices = [Index("day")])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val amountMl: Int,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "push_up_sets", indices = [Index("day")])
data class PushUpSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: String,
    val reps: Int,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "configuration_revisions", indices = [Index("effectiveLocalDay")])
data class ConfigurationRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveLocalDay: String,
    val waterGoalMl: Int,
    val pushUpGoalReps: Int,
    val creatineRequired: Boolean,
    val proteinRequired: Boolean,
    val recordedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAtEpochMs: Long = System.currentTimeMillis(),
    val sourceDay: String
)
