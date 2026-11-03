package com.hydrapush.tracker.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.hydrapush.tracker.backup.BackupManager
import com.hydrapush.tracker.data.local.*
import com.hydrapush.tracker.data.preferences.TrackerSettings
import com.hydrapush.tracker.data.preferences.TrackerSettingsStore
import com.hydrapush.tracker.domain.Achievement
import com.hydrapush.tracker.domain.DailyMetrics
import com.hydrapush.tracker.domain.ProgressMath
import com.hydrapush.tracker.reminders.ReminderScheduler
import com.hydrapush.tracker.widget.HydraPushAppWidgetProvider
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DayView(
    val snapshot: DailySnapshotEntity,
    val waters: List<WaterEntryEntity>,
    val sets: List<PushUpSetEntity>
) {
    val totalWater: Int get() = waters.sumOf { it.amountMl }
    val totalReps: Int get() = sets.sumOf { it.reps }
    val metrics: DailyMetrics get() = DailyMetrics(
        day = LocalDate.parse(snapshot.day),
        waterMl = totalWater,
        pushUpReps = totalReps,
        waterGoalMl = snapshot.waterGoalMl,
        pushUpGoalReps = snapshot.pushUpGoalReps,
        creatineRequired = snapshot.creatineRequired,
        proteinRequired = snapshot.proteinRequired,
        creatineDone = snapshot.creatineDone,
        proteinDone = snapshot.proteinDone
    )
}

class TrackerRepository private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var INSTANCE: TrackerRepository? = null

        fun getInstance(context: Context): TrackerRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrackerRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val database: TrackerDatabase = Room.databaseBuilder(
        appContext,
        TrackerDatabase::class.java,
        "hydrapush.db"
    ).build()

    private val dao: TrackerDao = database.dao()
    val settingsStore = TrackerSettingsStore(appContext)
    val settings: Flow<TrackerSettings> = settingsStore.settings
    private val mutex = Mutex()

    private fun today(): String = LocalDate.now().toString()

    val days: Flow<List<DayView>> = combine(
        dao.observeSnapshots(),
        dao.observeWater(),
        dao.observePushUpSets()
    ) { snapshots, water, sets ->
        val waterByDay = water.groupBy { it.day }
        val setsByDay = sets.groupBy { it.day }
        snapshots.map { s ->
            DayView(
                snapshot = s,
                waters = waterByDay[s.day] ?: emptyList(),
                sets = setsByDay[s.day] ?: emptyList()
            )
        }
    }

    val achievements: Flow<List<Achievement>> = combine(
        days,
        dao.observeAchievements()
    ) { allDays, unlockedFromDb ->
        val computed = ProgressMath.evaluateAchievements(allDays.map { it.metrics })
        val dbMap = unlockedFromDb.associateBy { it.achievementId }
        computed.map { ach ->
            val fromDb = dbMap[ach.id]
            if (fromDb != null) {
                ach.copy(isUnlocked = true, unlockedDate = fromDb.sourceDay)
            } else {
                ach
            }
        }
    }

    /**
     * Ensures today's snapshot exists. Backfills missing intermediate days
     * using historical configuration revisions without fabricating completion.
     */
    suspend fun reconcileToday() = mutex.withLock {
        val date = today()
        val currentSettings = settings.first()
        val recent = dao.mostRecentDay()

        if (recent != null) {
            var cursor = LocalDate.parse(recent).plusDays(1)
            val end = LocalDate.parse(date)
            while (cursor.isBefore(end)) {
                val dayStr = cursor.toString()
                if (dao.getSnapshot(dayStr) == null) {
                    val rev = dao.getRevisionOnOrBefore(dayStr)
                    dao.insertSnapshot(
                        DailySnapshotEntity(
                            day = dayStr,
                            waterGoalMl = rev?.waterGoalMl ?: currentSettings.waterGoalMl,
                            pushUpGoalReps = rev?.pushUpGoalReps ?: currentSettings.pushUpGoalReps,
                            creatineRequired = rev?.creatineRequired ?: currentSettings.creatineEnabled,
                            proteinRequired = rev?.proteinRequired ?: currentSettings.proteinEnabled,
                            creatineDone = false,
                            proteinDone = false
                        )
                    )
                }
                cursor = cursor.plusDays(1)
            }
        }

        if (dao.getSnapshot(date) == null) {
            dao.insertSnapshot(
                DailySnapshotEntity(
                    day = date,
                    waterGoalMl = currentSettings.waterGoalMl,
                    pushUpGoalReps = currentSettings.pushUpGoalReps,
                    creatineRequired = currentSettings.creatineEnabled,
                    proteinRequired = currentSettings.proteinEnabled,
                    creatineDone = false,
                    proteinDone = false
                )
            )
            dao.insertRevision(
                ConfigurationRevisionEntity(
                    effectiveLocalDay = date,
                    waterGoalMl = currentSettings.waterGoalMl,
                    pushUpGoalReps = currentSettings.pushUpGoalReps,
                    creatineRequired = currentSettings.creatineEnabled,
                    proteinRequired = currentSettings.proteinEnabled
                )
            )
        }

        checkAndPersistAchievements()
    }


    suspend fun addWater(amount: Int): Long {
        require(amount in 1..5000) { "Water entry must be between 1 and 5,000 ml" }
        reconcileToday()
        val entryId = dao.insertWater(WaterEntryEntity(day = today(), amountMl = amount))
        checkAndPersistAchievements()
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
        return entryId
    }

    suspend fun addSet(reps: Int): Long {
        require(reps in 1..1000) { "Set must be between 1 and 1,000 reps" }
        reconcileToday()
        val setId = dao.insertPushUpSet(PushUpSetEntity(day = today(), reps = reps))
        checkAndPersistAchievements()
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
        return setId
    }

    suspend fun undoWater(entryId: Long) {
        dao.removeWaterToday(entryId, today())
        checkAndPersistAchievements()
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun removeSet(setId: Long) {
        dao.removePushUpSetToday(setId, today())
        checkAndPersistAchievements()
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun setSupplementDone(creatine: Boolean, done: Boolean) = mutex.withLock {
        val date = today()
        val current = dao.getSnapshot(date) ?: return@withLock
        if (creatine && !current.creatineRequired) return@withLock
        if (!creatine && !current.proteinRequired) return@withLock

        dao.upsertSnapshot(
            if (creatine) current.copy(creatineDone = done)
            else current.copy(proteinDone = done)
        )
        checkAndPersistAchievements()
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun updateSettings(transform: (TrackerSettings) -> TrackerSettings) = mutex.withLock {
        val priorSettings = settings.first()
        val next = transform(priorSettings)
        settingsStore.save(next)

        val date = today()
        val priorSnapshot = dao.getSnapshot(date)
        if (priorSnapshot != null) {
            dao.upsertSnapshot(
                priorSnapshot.copy(
                    waterGoalMl = next.waterGoalMl,
                    pushUpGoalReps = next.pushUpGoalReps,
                    creatineRequired = next.creatineEnabled,
                    proteinRequired = next.proteinEnabled
                )
            )
        } else {
            dao.insertSnapshot(
                DailySnapshotEntity(
                    day = date,
                    waterGoalMl = next.waterGoalMl,
                    pushUpGoalReps = next.pushUpGoalReps,
                    creatineRequired = next.creatineEnabled,
                    proteinRequired = next.proteinEnabled
                )
            )
        }

        dao.insertRevision(
            ConfigurationRevisionEntity(
                effectiveLocalDay = date,
                waterGoalMl = next.waterGoalMl,
                pushUpGoalReps = next.pushUpGoalReps,
                creatineRequired = next.creatineEnabled,
                proteinRequired = next.proteinEnabled
            )
        )

        ReminderScheduler.scheduleAll(appContext, next)
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    private suspend fun checkAndPersistAchievements() {
        val allSnapshots = dao.getAllSnapshots()
        val allWater = dao.getAllWater().groupBy { it.day }
        val allPushups = dao.getAllPushUpSets().groupBy { it.day }

        val dayMetrics = allSnapshots.map { s ->
            DailyMetrics(
                day = LocalDate.parse(s.day),
                waterMl = (allWater[s.day] ?: emptyList()).sumOf { it.amountMl },
                pushUpReps = (allPushups[s.day] ?: emptyList()).sumOf { it.reps },
                waterGoalMl = s.waterGoalMl,
                pushUpGoalReps = s.pushUpGoalReps,
                creatineRequired = s.creatineRequired,
                proteinRequired = s.proteinRequired,
                creatineDone = s.creatineDone,
                proteinDone = s.proteinDone
            )
        }

        val evaluated = ProgressMath.evaluateAchievements(dayMetrics)
        val todayStr = today()
        val unlockedIds = evaluated.filter { it.isUnlocked }.map { it.id }.toSet()
        val existingAchievements = dao.getAllAchievements()

        // Insert new unlocks
        evaluated.filter { it.isUnlocked }.forEach { ach ->
            dao.insertAchievement(
                AchievementEntity(
                    achievementId = ach.id,
                    unlockedAtEpochMs = System.currentTimeMillis(),
                    sourceDay = ach.unlockedDate ?: todayStr
                )
            )
        }

        // Remove any falsely persisted unlock that no longer qualifies across entire history
        existingAchievements.forEach { existing ->
            if (existing.achievementId !in unlockedIds) {
                dao.deleteAchievement(existing.achievementId)
            }
        }
    }

    suspend fun exportBackup(uri: Uri): Result<Unit> = runCatching {
        val snapshots = dao.getAllSnapshots()
        val water = dao.getAllWater()
        val pushups = dao.getAllPushUpSets()
        val revisions = dao.getAllRevisions()
        val achievements = dao.getAllAchievements()
        val currentSettings = settings.first()

        val json = BackupManager.createBackupJson(
            snapshots, water, pushups, revisions, achievements, currentSettings
        )

        appContext.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(json.toByteArray(Charsets.UTF_8))
            os.flush()
        } ?: error("Failed to open destination output stream")
    }

    private fun readBackupText(uri: Uri): String {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: error("Failed to open backup input stream")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                require(total <= BackupManager.MAX_BACKUP_FILE_BYTES) {
                    "Backup exceeds maximum allowable size (15 MB)"
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    suspend fun importBackup(uri: Uri): Result<Unit> = runCatching {
        val json = readBackupText(uri)
        val payload = BackupManager.parseAndValidate(json)

        mutex.withLock {
            // Keep memory copy of current state for atomic rollback
            val priorSnapshots = dao.getAllSnapshots()
            val priorWater = dao.getAllWater()
            val priorPushups = dao.getAllPushUpSets()
            val priorRevisions = dao.getAllRevisions()
            val priorAchievements = dao.getAllAchievements()
            val priorSettings = settings.first()

            try {
                dao.restoreFullDatabase(
                    snapshots = payload.snapshots,
                    water = payload.water,
                    pushups = payload.pushups,
                    revisions = payload.revisions,
                    achievements = payload.achievements
                )
                settingsStore.save(payload.settings)
            } catch (e: Exception) {
                // Rollback to prior data
                dao.restoreFullDatabase(
                    snapshots = priorSnapshots,
                    water = priorWater,
                    pushups = priorPushups,
                    revisions = priorRevisions,
                    achievements = priorAchievements
                )
                settingsStore.save(priorSettings)
                throw e
            }
        }

        reconcileToday()
        ReminderScheduler.scheduleAll(appContext, payload.settings)
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }
}
