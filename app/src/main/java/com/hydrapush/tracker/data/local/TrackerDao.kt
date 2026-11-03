package com.hydrapush.tracker.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM daily_snapshots ORDER BY day ASC")
    fun observeSnapshots(): Flow<List<DailySnapshotEntity>>

    @Query("SELECT * FROM water_entries ORDER BY createdAtEpochMs ASC, id ASC")
    fun observeWater(): Flow<List<WaterEntryEntity>>

    @Query("SELECT * FROM push_up_sets ORDER BY createdAtEpochMs ASC, id ASC")
    fun observePushUpSets(): Flow<List<PushUpSetEntity>>

    @Query("SELECT * FROM achievements ORDER BY unlockedAtEpochMs ASC")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM daily_snapshots WHERE day = :day LIMIT 1")
    suspend fun getSnapshot(day: String): DailySnapshotEntity?

    @Query("SELECT * FROM daily_snapshots ORDER BY day ASC")
    suspend fun getAllSnapshots(): List<DailySnapshotEntity>

    @Query("SELECT * FROM water_entries ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getAllWater(): List<WaterEntryEntity>

    @Query("SELECT * FROM push_up_sets ORDER BY createdAtEpochMs ASC, id ASC")
    suspend fun getAllPushUpSets(): List<PushUpSetEntity>

    @Query("SELECT * FROM configuration_revisions ORDER BY effectiveLocalDay ASC, id ASC")
    suspend fun getAllRevisions(): List<ConfigurationRevisionEntity>

    @Query("SELECT * FROM achievements")
    suspend fun getAllAchievements(): List<AchievementEntity>

    @Query("SELECT MAX(day) FROM daily_snapshots")
    suspend fun mostRecentDay(): String?

    @Query("SELECT * FROM configuration_revisions WHERE effectiveLocalDay <= :day ORDER BY effectiveLocalDay DESC, id DESC LIMIT 1")
    suspend fun getRevisionOnOrBefore(day: String): ConfigurationRevisionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshot(record: DailySnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(record: DailySnapshotEntity)

    @Insert
    suspend fun insertWater(entry: WaterEntryEntity): Long

    @Insert
    suspend fun insertPushUpSet(set: PushUpSetEntity): Long

    @Insert
    suspend fun insertRevision(revision: ConfigurationRevisionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievement(achievement: AchievementEntity)

    @Query("DELETE FROM achievements WHERE achievementId = :achievementId")
    suspend fun deleteAchievement(achievementId: String)

    @Query("DELETE FROM water_entries WHERE id = :id AND day = :today")
    suspend fun removeWaterToday(id: Long, today: String): Int

    @Query("DELETE FROM push_up_sets WHERE id = :id AND day = :today")
    suspend fun removePushUpSetToday(id: Long, today: String): Int

    // Atomic full replacement for validated backup restoration
    @Transaction
    suspend fun restoreFullDatabase(
        snapshots: List<DailySnapshotEntity>,
        water: List<WaterEntryEntity>,
        pushups: List<PushUpSetEntity>,
        revisions: List<ConfigurationRevisionEntity>,
        achievements: List<AchievementEntity>
    ) {
        clearAllTables()
        insertAllSnapshots(snapshots)
        insertAllWater(water)
        insertAllPushUps(pushups)
        insertAllRevisions(revisions)
        insertAllAchievements(achievements)
    }

    @Query("DELETE FROM daily_snapshots")
    suspend fun clearSnapshots()

    @Query("DELETE FROM water_entries")
    suspend fun clearWater()

    @Query("DELETE FROM push_up_sets")
    suspend fun clearPushUps()

    @Query("DELETE FROM configuration_revisions")
    suspend fun clearRevisions()

    @Query("DELETE FROM achievements")
    suspend fun clearAchievements()

    @Transaction
    suspend fun clearAllTables() {
        clearWater()
        clearPushUps()
        clearSnapshots()
        clearRevisions()
        clearAchievements()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSnapshots(snapshots: List<DailySnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWater(water: List<WaterEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPushUps(pushups: List<PushUpSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRevisions(revisions: List<ConfigurationRevisionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAchievements(achievements: List<AchievementEntity>)
}
