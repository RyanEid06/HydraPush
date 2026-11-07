package com.hydrapush.tracker.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.hydrapush.tracker.backup.BackupManager
import com.hydrapush.tracker.data.local.AchievementEntity
import com.hydrapush.tracker.data.local.ConfigurationRevisionEntity
import com.hydrapush.tracker.data.local.DailySnapshotEntity
import com.hydrapush.tracker.data.local.PushUpSetEntity
import com.hydrapush.tracker.data.local.PullUpSetEntity
import com.hydrapush.tracker.data.local.TrackerDao
import com.hydrapush.tracker.data.local.TrackerDatabase
import com.hydrapush.tracker.data.local.WaterEntryEntity
import com.hydrapush.tracker.data.preferences.TrackerSettings
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class TrackerRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: TrackerRepository
    private lateinit var dao: TrackerDao
    private lateinit var database: TrackerDatabase

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("hydrapush.db")
        repository = TrackerRepository::class.java.getDeclaredConstructor(Context::class.java).apply {
            isAccessible = true
        }.newInstance(context)
        val daoField = TrackerRepository::class.java.getDeclaredField("dao").apply { isAccessible = true }
        dao = daoField.get(repository) as TrackerDao
        val dbField = TrackerRepository::class.java.getDeclaredField("database").apply { isAccessible = true }
        database = dbField.get(repository) as TrackerDatabase
        repository.settingsStore.save(TrackerSettings())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("hydrapush.db")
    }

    @Test
    fun unrelatedSettingsDoNotAddRequirementRevision() = runBlocking {
        repository.reconcileToday()
        val before = dao.getAllRevisions().size

        repository.updateSettings { it.copy(waterReminderEnabled = !it.waterReminderEnabled) }

        assertEquals(before, dao.getAllRevisions().size)
    }

    @Test
    fun freshReconcileCreatesOnlyOneEmptyTodaySnapshot() = runBlocking {
        repository.reconcileToday()

        val snapshots = dao.getAllSnapshots()
        assertEquals(1, snapshots.size)
        assertEquals(LocalDate.now().toString(), snapshots.single().day)
        assertEquals(10, snapshots.single().pullUpGoalReps)
        assertTrue(snapshots.single().pullUpsRequired)
        assertTrue(dao.getAllWater().isEmpty())
        assertTrue(dao.getAllPushUpSets().isEmpty())
        assertTrue(dao.getAllPullUpSets().isEmpty())
        assertTrue(dao.getAllAchievements().isEmpty())
    }

    @Test
    fun sameDayUpgradePreservesPerfectDayAndAllowsPullUpLogging() = runBlocking {
        val today = LocalDate.now().toString()
        repository.settingsStore.save(TrackerSettings(creatineEnabled = false, proteinEnabled = false))
        dao.insertSnapshot(DailySnapshotEntity(today, 2500, 100, false, false))
        dao.insertWater(WaterEntryEntity(day = today, amountMl = 2500))
        dao.insertPushUpSet(PushUpSetEntity(day = today, reps = 100))

        repository.reconcileToday()
        val upgraded = repository.days.first().single()
        assertEquals(10, upgraded.snapshot.pullUpGoalReps)
        assertFalse(upgraded.snapshot.pullUpsRequired)
        assertTrue(upgraded.metrics.perfect)
        assertEquals(1f, upgraded.metrics.progress)

        repository.addPullUpSet(10)
        val logged = repository.days.first().single()
        assertTrue(logged.metrics.pullUpsMet)
        assertTrue(logged.metrics.perfect)
    }

    @Test
    fun exportThenImportRestoresEveryRoomTableAndSettings() = runBlocking {
        val today = LocalDate.now().toString()
        val past = LocalDate.now().minusDays(3).toString()
        val settings = TrackerSettings(
            waterGoalMl = 3000,
            pushUpGoalReps = 80,
            creatineEnabled = false,
            proteinEnabled = true,
            waterReminderIntervalHours = 5,
            pushUpReminderTimeMinutes = 1010,
            creatineReminderTimeMinutes = 515,
            proteinReminderTimeMinutes = 1215,
            pullUpGoalReps = 12,
            pullUpReminderEnabled = true,
            pullUpReminderTimeMinutes = 1120
        )
        repository.settingsStore.save(settings)
        repository.reconcileToday()
        repository.addPullUpSet(5)
        val historicalSnapshot = DailySnapshotEntity(past, 2800, 90, true, false, true, false)
        dao.insertSnapshot(historicalSnapshot)
        dao.insertWater(WaterEntryEntity(day = past, amountMl = 2500, createdAtEpochMs = 101L))
        dao.insertPushUpSet(PushUpSetEntity(day = past, reps = 35, createdAtEpochMs = 102L))
        dao.insertRevision(ConfigurationRevisionEntity(
            effectiveLocalDay = past,
            waterGoalMl = 2800,
            pushUpGoalReps = 90,
            creatineRequired = true,
            proteinRequired = false,
            recordedAtEpochMs = 103L
        ))
        dao.insertAchievement(AchievementEntity("water_2l", 104L, past))
        val expectedSnapshots = dao.getAllSnapshots()
        val expectedWater = dao.getAllWater()
        val expectedPushups = dao.getAllPushUpSets()
        val expectedPullUps = dao.getAllPullUpSets()
        val expectedRevisions = dao.getAllRevisions()
        val expectedAchievements = dao.getAllAchievements()
        val backup = File(context.cacheDir, "roundtrip-${System.nanoTime()}.json")

        try {
            assertTrue(repository.exportBackup(Uri.fromFile(backup)).isSuccess)

            dao.restoreFullDatabase(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            repository.settingsStore.save(TrackerSettings())

            assertTrue(repository.importBackup(Uri.fromFile(backup)).isSuccess)
            assertEquals(expectedSnapshots, dao.getAllSnapshots())
            assertEquals(expectedWater, dao.getAllWater())
            assertEquals(expectedPushups, dao.getAllPushUpSets())
            assertEquals(expectedPullUps, dao.getAllPullUpSets())
            assertEquals(expectedRevisions, dao.getAllRevisions())
            assertEquals(expectedAchievements, dao.getAllAchievements())
            assertEquals(settings, repository.settings.first())
            assertTrue(dao.getSnapshot(today) != null)
        } finally {
            backup.delete()
        }
    }

    @Test
    fun failedPostRestoreReconciliationRollsBackAllRoomTablesAndSettings() = runBlocking {
        val today = LocalDate.now().toString()
        val past = LocalDate.now().minusDays(2).toString()
        val priorSettings = TrackerSettings(
            waterGoalMl = 2600,
            pushUpGoalReps = 90,
            creatineEnabled = true,
            proteinEnabled = false,
            waterReminderIntervalHours = 3,
            pushUpReminderTimeMinutes = 910,
            creatineReminderTimeMinutes = 480,
            proteinReminderTimeMinutes = 1180
        )
        repository.settingsStore.save(priorSettings)
        repository.reconcileToday()
        repository.addPullUpSet(3)
        dao.insertSnapshot(DailySnapshotEntity(past, 2400, 85, true, false, true, false))
        dao.insertWater(WaterEntryEntity(day = past, amountMl = 1100, createdAtEpochMs = 201L))
        dao.insertPushUpSet(PushUpSetEntity(day = past, reps = 41, createdAtEpochMs = 202L))
        dao.insertRevision(ConfigurationRevisionEntity(
            effectiveLocalDay = past,
            waterGoalMl = 2400,
            pushUpGoalReps = 85,
            creatineRequired = true,
            proteinRequired = false,
            recordedAtEpochMs = 203L
        ))
        dao.insertAchievement(AchievementEntity("pushup_50", 204L, past))
        val priorSnapshots = dao.getAllSnapshots()
        val priorWater = dao.getAllWater()
        val priorPushups = dao.getAllPushUpSets()
        val priorPullUps = dao.getAllPullUpSets()
        val priorRevisions = dao.getAllRevisions()
        val priorAchievements = dao.getAllAchievements()

        val importedSettings = TrackerSettings(
            waterGoalMl = 3100,
            pushUpGoalReps = 120,
            creatineEnabled = false,
            proteinEnabled = true,
            waterReminderIntervalHours = 7,
            pushUpReminderTimeMinutes = 1110,
            creatineReminderTimeMinutes = 540,
            proteinReminderTimeMinutes = 1260
        )
        val backup = BackupManager.createBackupJson(
            snapshots = listOf(
                DailySnapshotEntity(past, 2200, 70, false, true, false, true, createdAtEpochMs = 301L),
                DailySnapshotEntity(today, 3100, 120, false, true, false, false, createdAtEpochMs = 302L)
            ),
            water = listOf(WaterEntryEntity(id = 51, day = past, amountMl = 900, createdAtEpochMs = 303L)),
            pushups = listOf(PushUpSetEntity(id = 61, day = past, reps = 29, createdAtEpochMs = 304L)),
            revisions = listOf(
                ConfigurationRevisionEntity(71, past, 2200, 70, false, true, 305L),
                ConfigurationRevisionEntity(72, today, 3100, 120, false, true, 306L)
            ),
            achievements = listOf(AchievementEntity("streak_3", 307L, past)),
            settings = importedSettings
        )
        val backupFile = File(context.cacheDir, "rollback-${System.nanoTime()}.json")
        val repositoryDaoField = TrackerRepository::class.java.getDeclaredField("dao").apply { isAccessible = true }
        val actualDao = repositoryDaoField.get(repository) as TrackerDao
        val firstRestoreReached = AtomicBoolean(false)
        val failureInjected = AtomicBoolean(false)
        val faultingDao = Proxy.newProxyInstance(
            TrackerDao::class.java.classLoader,
            arrayOf(TrackerDao::class.java)
        ) { _, method, args ->
            if (method.name == "restoreFullDatabase") firstRestoreReached.set(true)
            if (method.name == "getSnapshot" && firstRestoreReached.get() && failureInjected.compareAndSet(false, true)) {
                throw IllegalStateException("Injected reconciliation failure after restore")
            }
            try {
                method.invoke(actualDao, *(args ?: emptyArray()))
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        } as TrackerDao

        try {
            backupFile.writeText(backup)
            repositoryDaoField.set(repository, faultingDao)

            val result = repository.importBackup(Uri.fromFile(backupFile))

            assertFalse(result.isSuccess)
            assertTrue("Import must reach replacement before the injected fault", firstRestoreReached.get())
            assertTrue("Reconciliation fault must be injected", failureInjected.get())
            assertEquals(priorSnapshots, dao.getAllSnapshots())
            assertEquals(priorWater, dao.getAllWater())
            assertEquals(priorPushups, dao.getAllPushUpSets())
            assertEquals(priorPullUps, dao.getAllPullUpSets())
            assertEquals(priorRevisions, dao.getAllRevisions())
            assertEquals(priorAchievements, dao.getAllAchievements())
            assertEquals(priorSettings, repository.settings.first())
        } finally {
            repositoryDaoField.set(repository, actualDao)
            backupFile.delete()
        }
    }

    @Test
    fun pullUpGoalChangesDoNotRewriteHistoricalDaysAndOnlyTodayCanBeEdited() = runBlocking {
        val yesterday = LocalDate.now().minusDays(1).toString()
        dao.insertSnapshot(DailySnapshotEntity(yesterday, 2500, 100, false, false, pullUpGoalReps = 7))
        val oldId = dao.insertPullUpSet(PullUpSetEntity(day = yesterday, reps = 7))

        repository.updateSettings { it.copy(pullUpGoalReps = 12, pullUpReminderEnabled = true) }
        assertTrue(repository.shouldShowReminder("pullup"))
        val first = repository.addPullUpSet(2)
        repository.addPullUpSet(10)
        assertFalse(repository.shouldShowReminder("pullup"))
        repository.removePullUpSet(oldId)
        repository.removePullUpSet(first)
        assertTrue(repository.shouldShowReminder("pullup"))

        val (past, current) = repository.days.first()
        assertEquals(7, past.totalPullUps)
        assertEquals(7, past.snapshot.pullUpGoalReps)
        assertEquals(10, current.totalPullUps)
        assertEquals(12, current.snapshot.pullUpGoalReps)
    }

    @Test
    fun invalidImportsLeaveDatabaseAndSettingsUntouched() = runBlocking {
        repository.reconcileToday()
        repository.addWater(600)
        repository.addSet(24)
        val settings = TrackerSettings(
            waterGoalMl = 3200,
            pushUpGoalReps = 75,
            creatineEnabled = false,
            waterReminderIntervalHours = 4,
            pushUpReminderTimeMinutes = 800
        )
        repository.settingsStore.save(settings)
        val expectedSnapshots = dao.getAllSnapshots()
        val expectedWater = dao.getAllWater()
        val expectedPushups = dao.getAllPushUpSets()
        val expectedRevisions = dao.getAllRevisions()
        val expectedAchievements = dao.getAllAchievements()
        val validBackup = BackupManager.createBackupJson(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), settings
        )
        val envelope = JSONObject(validBackup)
        val wrongAppData = JSONObject(envelope.getString("data")).put("appId", "com.example.other").toString(2)
        val wrongAppBackup = JSONObject()
            .put("data", wrongAppData)
            .put("checksum", sha256(wrongAppData))
            .toString()
        val corruptBackup = "not a backup"
        val wrongAppFile = File(context.cacheDir, "wrong-app-${System.nanoTime()}.json")
        val corruptFile = File(context.cacheDir, "corrupt-${System.nanoTime()}.json")
        val oversizedFile = File(context.cacheDir, "oversized-${System.nanoTime()}.json")

        try {
            wrongAppFile.writeText(wrongAppBackup)
            corruptFile.writeText(corruptBackup)
            oversizedFile.writeBytes(ByteArray(BackupManager.MAX_BACKUP_FILE_BYTES + 1))

            listOf(wrongAppFile, corruptFile, oversizedFile).forEach { file ->
                assertFalse("Import should reject ${file.name}", repository.importBackup(Uri.fromFile(file)).isSuccess)
                assertEquals(expectedSnapshots, dao.getAllSnapshots())
                assertEquals(expectedWater, dao.getAllWater())
                assertEquals(expectedPushups, dao.getAllPushUpSets())
                assertEquals(expectedRevisions, dao.getAllRevisions())
                assertEquals(expectedAchievements, dao.getAllAchievements())
                assertEquals(settings, repository.settings.first())
            }
        } finally {
            wrongAppFile.delete()
            corruptFile.delete()
            oversizedFile.delete()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    @Test
    fun requirementSettingsUpdateTodayAndRecordsOneRevision() = runBlocking {
        repository.reconcileToday()
        val original = dao.getSnapshot(LocalDate.now().toString())!!
        val before = dao.getAllRevisions().size

        repository.updateSettings { it.copy(waterGoalMl = it.waterGoalMl + 100) }

        assertEquals(original.waterGoalMl + 100, dao.getSnapshot(original.day)!!.waterGoalMl)
        assertEquals(before + 1, dao.getAllRevisions().size)
    }

    @Test
    fun reconcileAlignsImportedTodayRequirementsButPreservesCompletion() = runBlocking {
        repository.reconcileToday()
        val day = LocalDate.now().toString()
        val original = dao.getSnapshot(day)!!
        dao.upsertSnapshot(
            original.copy(
                waterGoalMl = original.waterGoalMl + 500,
                creatineRequired = false,
                creatineDone = true
            )
        )

        repository.reconcileToday()

        val reconciled = dao.getSnapshot(day)!!
        assertEquals(original.waterGoalMl, reconciled.waterGoalMl)
        assertTrue(reconciled.creatineRequired)
        assertTrue(reconciled.creatineDone)
    }

    @Test
    fun concurrentSupplementTogglesDoNotLoseAnUpdate() = runBlocking {
        repository.reconcileToday()

        val first = async { repository.toggleSupplement(creatine = true) }
        val second = async { repository.toggleSupplement(creatine = true) }
        first.await()
        second.await()

        assertEquals(false, dao.getSnapshot(LocalDate.now().toString())!!.creatineDone)
    }

    @Test
    fun goalChangesImmediatelyRevokeUnqualifiedAchievements() = runBlocking {
        repository.updateSettings { it.copy(waterGoalMl = 150, pushUpGoalReps = 1, creatineEnabled = false, proteinEnabled = false) }
        repository.addWater(150)
        repository.addSet(1)
        repository.addPullUpSet(10)
        assertTrue(dao.getAllAchievements().any { it.achievementId == "first_perfect" })

        repository.updateSettings { it.copy(waterGoalMl = 2500) }

        assertTrue(dao.getAllAchievements().none { it.achievementId == "first_perfect" })
    }

    @Test
    fun rapidWaterAndSupplementUpdatesPersistEveryOperation() = runBlocking {
        repository.reconcileToday()
        val waterIds = (1..30).map { async { repository.addWater(150) } }.awaitAll()
        (1..31).map { async { repository.toggleSupplement(creatine = true) } }.awaitAll()
        assertEquals(30, waterIds.toSet().size)
        assertEquals(4500, dao.getAllWater().sumOf { it.amountMl })
        assertTrue(dao.getSnapshot(LocalDate.now().toString())!!.creatineDone)
    }

    @Test
    fun liveEntryAndGoalValidationRejectsValuesAboveSharedLimits() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.addWater(5001) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.addSet(1001) } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.settingsStore.save(TrackerSettings(waterGoalMl = 20001)) }
        }
        assertTrue(dao.getAllWater().isEmpty())
        assertTrue(dao.getAllPushUpSets().isEmpty())
    }

    @Test
    fun undoDeletesExactTodayIdsAndNeverPastEntries() = runBlocking {
        repository.reconcileToday()
        val past = LocalDate.now().minusDays(1).toString()
        dao.insertSnapshot(com.hydrapush.tracker.data.local.DailySnapshotEntity(past, 2500, 100, true, true))
        val oldWater = dao.insertWater(com.hydrapush.tracker.data.local.WaterEntryEntity(day = past, amountMl = 150))
        val oldSet = dao.insertPushUpSet(com.hydrapush.tracker.data.local.PushUpSetEntity(day = past, reps = 10))
        val firstWater = repository.addWater(150)
        val secondWater = repository.addWater(250)
        val firstSet = repository.addSet(10)
        val secondSet = repository.addSet(20)

        repository.undoWater(firstWater)
        repository.removeSet(firstSet)
        repository.undoWater(oldWater)
        repository.removeSet(oldSet)

        assertEquals(setOf(oldWater, secondWater), dao.getAllWater().map { it.id }.toSet())
        assertEquals(setOf(oldSet, secondSet), dao.getAllPushUpSets().map { it.id }.toSet())
    }

    @Test
    fun changingOptionalRequirementPreservesPastAndCompletionOnReenable() = runBlocking {
        repository.reconcileToday()
        val today = LocalDate.now().toString()
        val past = LocalDate.now().minusDays(1).toString()
        val historical = com.hydrapush.tracker.data.local.DailySnapshotEntity(past, 3000, 150, true, false, true, false)
        dao.insertSnapshot(historical)
        repository.updateSettings { it.copy(creatineReminderEnabled = true, proteinReminderEnabled = true) }
        repository.toggleSupplement(creatine = true)

        repository.updateSettings { it.copy(creatineEnabled = false, proteinEnabled = false, waterGoalMl = 4000) }
        assertEquals(historical, dao.getSnapshot(past))
        assertTrue(!dao.getSnapshot(today)!!.creatineRequired)
        assertFalse(repository.settings.first().creatineReminderEnabled)
        assertFalse(repository.settings.first().proteinReminderEnabled)
        repository.toggleSupplement(creatine = true)
        repository.updateSettings { it.copy(creatineEnabled = true) }

        assertTrue(dao.getSnapshot(today)!!.creatineDone)
        assertFalse(repository.settings.first().creatineReminderEnabled)
        assertEquals(historical, dao.getSnapshot(past))
    }

    @Test
    fun completedHabitReminderIsSuppressedAndDisabledReminderIsSuppressed() = runBlocking {
        repository.reconcileToday()
        repository.settingsStore.save(TrackerSettings(waterReminderEnabled = true, waterGoalMl = 250))
        repository.addWater(250)

        assertTrue(!repository.shouldShowReminder("water"))
        repository.settingsStore.save(TrackerSettings(waterReminderEnabled = false))
        assertTrue(!repository.shouldShowReminder("water"))
    }
}
