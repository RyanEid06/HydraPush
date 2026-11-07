package com.hydrapush.tracker.backup

import com.hydrapush.tracker.data.local.*
import com.hydrapush.tracker.data.preferences.TrackerSettings
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupManagerTest {
    @Test
    fun versionOneBackupKeepsLegacyDaysAndDefaultsPullUpSettings() {
        val data = """
            {
              "appId": "com.hydrapush.tracker", "version": 1,
              "snapshots": [{"day":"2026-11-01","waterGoalMl":500,"pushUpGoalReps":10,
                             "creatineRequired":false,"proteinRequired":false}],
              "water": [{"id":1,"day":"2026-11-01","amountMl":10000,"createdAtEpochMs":1234}],
              "pushups": [{"id":1,"day":"2026-11-01","reps":2000,"createdAtEpochMs":2345}]
            }
        """.trimIndent()
        val checksum = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val restored = BackupManager.parseAndValidate(JSONObject().put("data", data).put("checksum", checksum).toString())
        assertEquals(1, restored.version)
        assertNull(restored.snapshots.single().pullUpGoalReps)
        assertEquals(1234L, restored.water.single().createdAtEpochMs)
        assertEquals(2345L, restored.pushups.single().createdAtEpochMs)
        assertTrue(restored.pullups.isEmpty())
        assertEquals(10, restored.settings.pullUpGoalReps)
        assertFalse(restored.settings.pullUpReminderEnabled)
        val reexported = BackupManager.parseAndValidate(BackupManager.createBackupJson(
            restored.snapshots, restored.water, restored.pushups, restored.revisions,
            restored.achievements, restored.settings, restored.pullups
        ))
        assertEquals(restored.water, reexported.water)
        assertEquals(restored.pushups, reexported.pushups)
    }

    @Test
    fun importRejectsEntriesAboveSupportedHistoricalLimits() {
        val snapshot = DailySnapshotEntity("2026-11-01", 2500, 100, true, true)
        listOf(
            listOf(WaterEntryEntity(1, snapshot.day, 10001)) to emptyList<PushUpSetEntity>(),
            emptyList<WaterEntryEntity>() to listOf(PushUpSetEntity(1, snapshot.day, 2001))
        ).forEach { (water, pushups) ->
            val json = BackupManager.createBackupJson(
                listOf(snapshot), water, pushups, emptyList(), emptyList(), TrackerSettings()
            )
            assertThrows(IllegalArgumentException::class.java) { BackupManager.parseAndValidate(json) }
        }
    }

    @Test
    fun liveMaximumEntriesAndGoalsRoundTrip() {
        val snapshot = DailySnapshotEntity("2026-11-01", 20000, 10000, false, false)
        val json = BackupManager.createBackupJson(
            listOf(snapshot), listOf(WaterEntryEntity(1, snapshot.day, 5000)),
            listOf(PushUpSetEntity(1, snapshot.day, 1000)), emptyList(), emptyList(),
            TrackerSettings(waterGoalMl = 20000, pushUpGoalReps = 10000)
        )
        val restored = BackupManager.parseAndValidate(json)
        assertEquals(5000, restored.water.single().amountMl)
        assertEquals(1000, restored.pushups.single().reps)
        assertEquals(20000, restored.settings.waterGoalMl)
        assertEquals(10000, restored.settings.pushUpGoalReps)
    }

    @Test
    fun roundTripBackupPayloadPreservesDataAndReminders() {
        val snapshots = listOf(
            DailySnapshotEntity("2026-11-01", 2500, 100, true, true, true, true),
            DailySnapshotEntity("2026-11-02", 2800, 120, false, false, false, false, pullUpGoalReps = 12, pullUpsRequired = false)
        )
        val water = listOf(
            WaterEntryEntity(1, "2026-11-01", 500, 1000L),
            WaterEntryEntity(2, "2026-11-01", 2000, 2000L)
        )
        val pushups = listOf(
            PushUpSetEntity(1, "2026-11-01", 25, 1000L),
            PushUpSetEntity(2, "2026-11-01", 75, 2000L)
        )
        val revisions = listOf(
            ConfigurationRevisionEntity(1, "2026-11-01", 2500, 100, true, true, 1000L),
            ConfigurationRevisionEntity(2, "2026-11-02", 2800, 120, false, false, 2000L, pullUpGoalReps = 12)
        )
        val achievements = listOf(
            AchievementEntity("first_perfect", 1000L, "2026-11-01")
        )
        val nonDefaultSettings = TrackerSettings(
            waterGoalMl = 3200,
            pushUpGoalReps = 150,
            creatineEnabled = true,
            proteinEnabled = true,
            themeMode = "LIGHT",
            waterReminderEnabled = true,
            waterReminderIntervalHours = 3,
            pushUpReminderEnabled = true,
            pushUpReminderTimeMinutes = 17 * 60 + 30, // 5:30 PM
            creatineReminderEnabled = true,
            creatineReminderTimeMinutes = 8 * 60 + 15, // 8:15 AM
            proteinReminderEnabled = false,
            proteinReminderTimeMinutes = 20 * 60 + 45, // 8:45 PM
            pullUpGoalReps = 12,
            pullUpReminderEnabled = true,
            pullUpReminderTimeMinutes = 19 * 60 + 15
        )
        val pullups = listOf(PullUpSetEntity(1, "2026-11-02", 5, 2020L))

        val json = BackupManager.createBackupJson(
            snapshots, water, pushups, revisions, achievements, nonDefaultSettings, pullups
        )

        val restored = BackupManager.parseAndValidate(json)

        assertEquals(BackupManager.CURRENT_BACKUP_VERSION, restored.version)
        assertEquals(2, restored.snapshots.size)
        assertEquals(2500, restored.snapshots[0].waterGoalMl)
        assertEquals(2, restored.water.size)
        assertEquals(2, restored.pushups.size)
        assertEquals(revisions, restored.revisions)
        assertEquals(snapshots, restored.snapshots)
        assertEquals(pullups, restored.pullups)
        assertEquals(nonDefaultSettings, restored.settings)
        assertEquals(1, restored.achievements.size)

        // Verify non-default reminder settings round-trip exactly
        assertEquals(3200, restored.settings.waterGoalMl)
        assertEquals(150, restored.settings.pushUpGoalReps)
        assertEquals(true, restored.settings.creatineEnabled)
        assertEquals(true, restored.settings.proteinEnabled)
        assertEquals("LIGHT", restored.settings.themeMode)
        assertEquals(true, restored.settings.waterReminderEnabled)
        assertEquals(3, restored.settings.waterReminderIntervalHours)
        assertEquals(true, restored.settings.pushUpReminderEnabled)
        assertEquals(17 * 60 + 30, restored.settings.pushUpReminderTimeMinutes)
        assertEquals(true, restored.settings.creatineReminderEnabled)
        assertEquals(8 * 60 + 15, restored.settings.creatineReminderTimeMinutes)
        assertEquals(false, restored.settings.proteinReminderEnabled)
        assertEquals(20 * 60 + 45, restored.settings.proteinReminderTimeMinutes)
    }

    @Test
    fun rejectsPullUpSetsOnLegacyDays() {
        val day = "2026-11-01"
        val json = BackupManager.createBackupJson(
            listOf(DailySnapshotEntity(day, 2500, 100, true, true)),
            emptyList(), emptyList(), emptyList(), emptyList(), TrackerSettings(),
            listOf(PullUpSetEntity(1, day, 2, 1000L))
        )
        assertThrows(IllegalArgumentException::class.java) { BackupManager.parseAndValidate(json) }
    }

    @Test
    fun defaultV1SettingsAreLightAndRemindersOptIn() {
        val defaults = TrackerSettings()
        assertEquals("LIGHT", defaults.themeMode)
        assertFalse(defaults.waterReminderEnabled)
        assertFalse(defaults.pushUpReminderEnabled)
        assertFalse(defaults.creatineReminderEnabled)
        assertFalse(defaults.proteinReminderEnabled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tamperedBackupThrowsException() {
        val snapshots = listOf(DailySnapshotEntity("2026-11-01", 2500, 100, true, true))
        val json = BackupManager.createBackupJson(
            snapshots, emptyList(), emptyList(), emptyList(), emptyList(), TrackerSettings()
        )
        val tamperedJson = json.replace("2500", "9999")
        BackupManager.parseAndValidate(tamperedJson)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDateThrowsException() {
        val snapshots = listOf(DailySnapshotEntity("invalid-date", 2500, 100, true, true))
        val json = BackupManager.createBackupJson(
            snapshots, emptyList(), emptyList(), emptyList(), emptyList(), TrackerSettings()
        )
        BackupManager.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateSnapshotDaysThrowsException() {
        val snapshots = listOf(
            DailySnapshotEntity("2026-11-01", 2500, 100, true, true),
            DailySnapshotEntity("2026-11-01", 2600, 100, true, true)
        )
        val json = BackupManager.createBackupJson(
            snapshots, emptyList(), emptyList(), emptyList(), emptyList(), TrackerSettings()
        )
        BackupManager.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun orphanWaterEntryThrowsException() {
        val json = BackupManager.createBackupJson(
            snapshots = listOf(DailySnapshotEntity("2026-11-01", 2500, 100, true, true)),
            water = listOf(WaterEntryEntity(1, "2026-11-02", 500, 1000L)),
            pushups = emptyList(),
            revisions = emptyList(),
            achievements = emptyList(),
            settings = TrackerSettings()
        )
        BackupManager.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyJsonThrowsException() {
        BackupManager.parseAndValidate("")
    }
}
