package com.hydrapush.tracker.backup

import com.hydrapush.tracker.data.local.*
import com.hydrapush.tracker.data.preferences.TrackerSettings
import org.junit.Assert.*
import org.junit.Test

class BackupManagerTest {

    @Test
    fun roundTripBackupPayloadPreservesDataAndReminders() {
        val snapshots = listOf(
            DailySnapshotEntity("2026-11-01", 2500, 100, true, true, true, true),
            DailySnapshotEntity("2026-11-02", 2800, 120, false, false, false, false)
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
            ConfigurationRevisionEntity(1, "2026-11-01", 2500, 100, true, true, 1000L)
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
            proteinReminderTimeMinutes = 20 * 60 + 45  // 8:45 PM
        )

        val json = BackupManager.createBackupJson(
            snapshots, water, pushups, revisions, achievements, nonDefaultSettings
        )

        val restored = BackupManager.parseAndValidate(json)

        assertEquals(BackupManager.CURRENT_BACKUP_VERSION, restored.version)
        assertEquals(2, restored.snapshots.size)
        assertEquals(2500, restored.snapshots[0].waterGoalMl)
        assertEquals(2, restored.water.size)
        assertEquals(2, restored.pushups.size)
        assertEquals(1, restored.revisions.size)
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
    fun emptyJsonThrowsException() {
        BackupManager.parseAndValidate("")
    }
}
