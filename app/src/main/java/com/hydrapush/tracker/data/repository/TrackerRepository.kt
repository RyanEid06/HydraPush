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
import com.hydrapush.tracker.domain.ValidationLimits
import com.hydrapush.tracker.reminders.ReminderScheduler
import com.hydrapush.tracker.widget.HydraPushAppWidgetProvider
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DayView(
    val snapshot: DailySnapshotEntity,
    val waters: List<WaterEntryEntity>,
    val sets: List<PushUpSetEntity>,
    val pullUpSets: List<PullUpSetEntity> = emptyList()
) {
    val totalWater: Int get() = waters.sumOf { it.amountMl }
    val totalReps: Int get() = sets.sumOf { it.reps }
    val totalPullUps: Int get() = pullUpSets.sumOf { it.reps }
    val metrics: DailyMetrics get() = DailyMetrics(
        day = LocalDate.parse(snapshot.day),
        waterMl = totalWater,
        pushUpReps = totalReps,
        waterGoalMl = snapshot.waterGoalMl,
        pushUpGoalReps = snapshot.pushUpGoalReps,
        creatineRequired = snapshot.creatineRequired,
        proteinRequired = snapshot.proteinRequired,
        creatineDone = snapshot.creatineDone,
        proteinDone = snapshot.proteinDone,
        pullUpReps = totalPullUps,
        pullUpGoalReps = snapshot.pullUpGoalReps,
        pullUpsRequired = snapshot.pullUpsRequired
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
    ).addMigrations(MIGRATION_1_2).build()

    private val dao: TrackerDao = database.dao()
    val settingsStore = TrackerSettingsStore(appContext)
    val settings: Flow<TrackerSettings> = settingsStore.settings
    private val mutex = Mutex()

    private fun today(): String = LocalDate.now().toString()

    val days: Flow<List<DayView>> = combine(
        dao.observeSnapshots(),
        dao.observeWater(),
        dao.observePushUpSets(),
        dao.observePullUpSets()
    ) { snapshots, water, sets, pullUps ->
        val waterByDay = water.groupBy { it.day }
        val setsByDay = sets.groupBy { it.day }
        val pullUpsByDay = pullUps.groupBy { it.day }
        snapshots.map { s ->
            DayView(
                snapshot = s,
                waters = waterByDay[s.day] ?: emptyList(),
                sets = setsByDay[s.day].orEmpty(),
                pullUpSets = pullUpsByDay[s.day].orEmpty()
            )
        }
    }

    val achievements: Flow<List<Achievement>> = combine(
        days,
        dao.observeAchievements()
    ) { allDays, unlockedFromDb ->
        val today = today()
        val computed = ProgressMath.evaluateAchievements(allDays.filter { it.snapshot.day <= today }.map { it.metrics })
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
        reconcileTodayLocked(LocalDate.parse(today()))
    }

    private suspend fun reconcileTodayLocked(date: LocalDate): DailySnapshotEntity {
        val dateString = date.toString()
        val currentSettings = settings.first()
        val recent = dao.mostRecentDay()

        if (recent != null) {
            var cursor = LocalDate.parse(recent).plusDays(1)
            while (cursor.isBefore(date)) {
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
                            proteinDone = false,
                            pullUpGoalReps = rev?.pullUpGoalReps
                        )
                    )
                }
                cursor = cursor.plusDays(1)
            }
        }

        var snapshot = dao.getSnapshot(dateString)
        if (snapshot == null) {
            snapshot = DailySnapshotEntity(
                day = dateString,
                waterGoalMl = currentSettings.waterGoalMl,
                pushUpGoalReps = currentSettings.pushUpGoalReps,
                creatineRequired = currentSettings.creatineEnabled,
                proteinRequired = currentSettings.proteinEnabled,
                pullUpGoalReps = currentSettings.pullUpGoalReps
            )
            dao.insertSnapshot(snapshot)
        } else if (
            snapshot.waterGoalMl != currentSettings.waterGoalMl ||
            snapshot.pushUpGoalReps != currentSettings.pushUpGoalReps ||
            snapshot.pullUpGoalReps != currentSettings.pullUpGoalReps ||
            snapshot.creatineRequired != currentSettings.creatineEnabled ||
            snapshot.proteinRequired != currentSettings.proteinEnabled
        ) {
            snapshot = snapshot.copy(
                waterGoalMl = currentSettings.waterGoalMl,
                pushUpGoalReps = currentSettings.pushUpGoalReps,
                creatineRequired = currentSettings.creatineEnabled,
                proteinRequired = currentSettings.proteinEnabled,
                pullUpGoalReps = currentSettings.pullUpGoalReps
            )
            dao.upsertSnapshot(snapshot)
        }

        val latestRevision = dao.getRevisionOnOrBefore(dateString)
        if (latestRevision == null || !latestRevision.matches(currentSettings)) {
            dao.insertRevision(
                ConfigurationRevisionEntity(
                    effectiveLocalDay = dateString,
                    waterGoalMl = currentSettings.waterGoalMl,
                    pushUpGoalReps = currentSettings.pushUpGoalReps,
                    creatineRequired = currentSettings.creatineEnabled,
                    proteinRequired = currentSettings.proteinEnabled,
                    pullUpGoalReps = currentSettings.pullUpGoalReps
                )
            )
        }

        checkAndPersistAchievements(dateString)
        return snapshot
    }

    private fun ConfigurationRevisionEntity.matches(settings: TrackerSettings): Boolean =
        waterGoalMl == settings.waterGoalMl &&
            pushUpGoalReps == settings.pushUpGoalReps &&
            pullUpGoalReps == settings.pullUpGoalReps &&
            creatineRequired == settings.creatineEnabled &&
            proteinRequired == settings.proteinEnabled


    suspend fun addWater(amount: Int): Long {
        require(amount in ValidationLimits.WATER_ENTRY) { "Water entry must be between 1 and 5,000 ml" }
        return mutex.withLock {
            val date = today()
            reconcileTodayLocked(LocalDate.parse(date))
            val entryId = dao.insertWater(WaterEntryEntity(day = date, amountMl = amount))
            checkAndPersistAchievements(date)
            HydraPushAppWidgetProvider.updateAllWidgets(appContext)
            entryId
        }
    }

    suspend fun addSet(reps: Int): Long {
        require(reps in ValidationLimits.PUSH_UP_SET) { "Set must be between 1 and 1,000 reps" }
        return mutex.withLock {
            val date = today()
            reconcileTodayLocked(LocalDate.parse(date))
            val setId = dao.insertPushUpSet(PushUpSetEntity(day = date, reps = reps))
            checkAndPersistAchievements(date)
            HydraPushAppWidgetProvider.updateAllWidgets(appContext)
            setId
        }
    }

    suspend fun undoWater(entryId: Long) = mutex.withLock {
        val date = today()
        reconcileTodayLocked(LocalDate.parse(date))
        dao.removeWaterToday(entryId, date)
        checkAndPersistAchievements(date)
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun addPullUpSet(reps: Int): Long {
        require(reps in ValidationLimits.PULL_UP_SET) { "Set must be between 1 and 1,000 reps" }
        return mutex.withLock {
            val date = today()
            reconcileTodayLocked(LocalDate.parse(date))
            val id = dao.insertPullUpSet(PullUpSetEntity(day = date, reps = reps))
            checkAndPersistAchievements(date)
            HydraPushAppWidgetProvider.updateAllWidgets(appContext)
            id
        }
    }

    suspend fun removePullUpSet(setId: Long) = mutex.withLock {
        val date = today()
        reconcileTodayLocked(LocalDate.parse(date))
        dao.removePullUpSetToday(setId, date)
        checkAndPersistAchievements(date)
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun removeSet(setId: Long) = mutex.withLock {
        val date = today()
        reconcileTodayLocked(LocalDate.parse(date))
        dao.removePushUpSetToday(setId, date)
        checkAndPersistAchievements(date)
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun setSupplementDone(creatine: Boolean, done: Boolean) = mutex.withLock {
        val date = LocalDate.parse(today())
        val current = reconcileTodayLocked(date)
        if (creatine && !current.creatineRequired) return@withLock
        if (!creatine && !current.proteinRequired) return@withLock

        dao.upsertSnapshot(
            if (creatine) current.copy(creatineDone = done)
            else current.copy(proteinDone = done)
        )
        checkAndPersistAchievements(date.toString())
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun toggleSupplement(creatine: Boolean) = mutex.withLock {
        val date = LocalDate.parse(today())
        val current = reconcileTodayLocked(date)
        if (creatine && !current.creatineRequired) return@withLock
        if (!creatine && !current.proteinRequired) return@withLock

        dao.upsertSnapshot(
            if (creatine) current.copy(creatineDone = !current.creatineDone)
            else current.copy(proteinDone = !current.proteinDone)
        )
        checkAndPersistAchievements(date.toString())
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun shouldShowReminder(type: String): Boolean = mutex.withLock {
        if (!ReminderScheduler.notificationsAllowed(appContext)) return@withLock false
        val date = LocalDate.parse(today())
        val snapshot = reconcileTodayLocked(date)
        val settingsNow = settings.first()
        when (type) {
            "water" -> settingsNow.waterReminderEnabled &&
                dao.getAllWater().filter { it.day == date.toString() }.sumOf { it.amountMl } < snapshot.waterGoalMl
            "pushup" -> settingsNow.pushUpReminderEnabled &&
                dao.getAllPushUpSets().filter { it.day == date.toString() }.sumOf { it.reps } < snapshot.pushUpGoalReps
            "pullup" -> settingsNow.pullUpReminderEnabled &&
                dao.getPullUpTotal(date.toString()) < settingsNow.pullUpGoalReps
            "creatine" -> settingsNow.creatineEnabled && settingsNow.creatineReminderEnabled && !snapshot.creatineDone
            "protein" -> settingsNow.proteinEnabled && settingsNow.proteinReminderEnabled && !snapshot.proteinDone
            else -> false
        }
    }

    suspend fun updateSettings(transform: (TrackerSettings) -> TrackerSettings) = mutex.withLock {
        val priorSettings = settings.first()
        val next = transform(priorSettings)
        val date = LocalDate.parse(today())
        val dateString = date.toString()
        val current = reconcileTodayLocked(date)
        val requirementChanged = priorSettings.waterGoalMl != next.waterGoalMl ||
            priorSettings.pushUpGoalReps != next.pushUpGoalReps ||
            priorSettings.pullUpGoalReps != next.pullUpGoalReps ||
            priorSettings.creatineEnabled != next.creatineEnabled ||
            priorSettings.proteinEnabled != next.proteinEnabled

        settingsStore.save(next)
        if (requirementChanged) {
            dao.upsertSnapshot(
                current.copy(
                    waterGoalMl = next.waterGoalMl,
                    pushUpGoalReps = next.pushUpGoalReps,
                    creatineRequired = next.creatineEnabled,
                    proteinRequired = next.proteinEnabled,
                    pullUpGoalReps = next.pullUpGoalReps
                )
            )
            dao.insertRevision(
                ConfigurationRevisionEntity(
                    effectiveLocalDay = dateString,
                    waterGoalMl = next.waterGoalMl,
                    pushUpGoalReps = next.pushUpGoalReps,
                    creatineRequired = next.creatineEnabled,
                    proteinRequired = next.proteinEnabled,
                    pullUpGoalReps = next.pullUpGoalReps
                )
            )
            checkAndPersistAchievements(dateString)
        }

        ReminderScheduler.scheduleAll(appContext, next)
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }

    private suspend fun checkAndPersistAchievements(todayString: String) {
        val allSnapshots = dao.getAllSnapshots()
        val allWater = dao.getAllWater().groupBy { it.day }
        val allPushups = dao.getAllPushUpSets().groupBy { it.day }
        val allPullUps = dao.getAllPullUpSets().groupBy { it.day }

        val dayMetrics = allSnapshots.filter { it.day <= todayString }.map { s ->
            DayView(s, allWater[s.day].orEmpty(), allPushups[s.day].orEmpty(), allPullUps[s.day].orEmpty()).metrics
        }

        val evaluated = ProgressMath.evaluateAchievements(dayMetrics)
        val unlockedIds = evaluated.filter { it.isUnlocked }.map { it.id }.toSet()
        val existingAchievements = dao.getAllAchievements()

        // Insert new unlocks
        evaluated.filter { it.isUnlocked }.forEach { ach ->
            dao.insertAchievement(
                AchievementEntity(
                    achievementId = ach.id,
                    unlockedAtEpochMs = System.currentTimeMillis(),
                    sourceDay = ach.unlockedDate ?: todayString
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
        val json = mutex.withLock {
            BackupManager.createBackupJson(
                dao.getAllSnapshots(),
                dao.getAllWater(),
                dao.getAllPushUpSets(),
                dao.getAllRevisions(),
                dao.getAllAchievements(),
                settings.first(),
                dao.getAllPullUpSets()
            )
        }
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

        val importedSettings = if (ReminderScheduler.notificationsAllowed(appContext)) {
            payload.settings
        } else {
            payload.settings.copy(
                waterReminderEnabled = false,
                pushUpReminderEnabled = false,
                creatineReminderEnabled = false,
                proteinReminderEnabled = false
            )
        }

        mutex.withLock {
            // Keep memory copy of current state for atomic rollback
            val priorSnapshots = dao.getAllSnapshots()
            val priorWater = dao.getAllWater()
            val priorPushups = dao.getAllPushUpSets()
            val priorPullUps = dao.getAllPullUpSets()
            val priorRevisions = dao.getAllRevisions()
            val priorAchievements = dao.getAllAchievements()
            val priorSettings = settings.first()

            try {
                dao.restoreFullDatabase(
                    snapshots = payload.snapshots,
                    water = payload.water,
                    pushups = payload.pushups,
                    revisions = payload.revisions,
                    achievements = payload.achievements,
                    pullups = payload.pullups
                )
                settingsStore.save(importedSettings)
                reconcileTodayLocked(LocalDate.parse(today()))
            } catch (e: Throwable) {
                // Rollback to prior data
                withContext(NonCancellable) {
                    dao.restoreFullDatabase(
                        snapshots = priorSnapshots,
                        water = priorWater,
                        pushups = priorPushups,
                        revisions = priorRevisions,
                        achievements = priorAchievements,
                        pullups = priorPullUps
                    )
                    settingsStore.save(priorSettings)
                }
                throw e
            }
        }

        mutex.withLock {
            ReminderScheduler.scheduleAll(appContext, settings.first())
        }
        HydraPushAppWidgetProvider.updateAllWidgets(appContext)
    }
}
