package com.hydrapush.tracker.backup

import com.hydrapush.tracker.data.local.*
import com.hydrapush.tracker.data.preferences.TrackerSettings
import org.junit.Assert.*
import org.junit.Test

class BackupManagerTest {

    @Test
    fun roundTripBackupPayloadPreservesData() {
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
        val settings = TrackerSettings(
            waterGoalMl = 2600,
            pushUpGoalReps = 110,
            creatineEnabled = true,
            proteinEnabled = false,
            themeMode = "LIGHT"
        )

        val json = BackupManager.createBackupJson(
            snapshots, water, pushups, revisions, achievements, settings
        )

        val restored = BackupManager.parseAndValidate(json)

        assertEquals(BackupManager.CURRENT_BACKUP_VERSION, restored.version)
        assertEquals(2, restored.snapshots.size)
        assertEquals(2500, restored.snapshots[0].waterGoalMl)
        assertEquals(2, restored.water.size)
        assertEquals(2, restored.pushups.size)
        assertEquals(1, restored.revisions.size)
        assertEquals(1, restored.achievements.size)
        assertEquals(2600, restored.settings.waterGoalMl)
        assertEquals(false, restored.settings.proteinEnabled)
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
}
