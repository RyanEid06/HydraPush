package com.hydrapush.tracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.trackerDataStore by preferencesDataStore(name = "hydrapush_settings")

data class TrackerSettings(
    val waterGoalMl: Int = 2500,
    val pushUpGoalReps: Int = 100,
    val creatineEnabled: Boolean = true,
    val proteinEnabled: Boolean = true,
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val waterReminderEnabled: Boolean = true,
    val waterReminderIntervalHours: Int = 2,
    val pushUpReminderEnabled: Boolean = true,
    val pushUpReminderTimeMinutes: Int = 18 * 60, // 6:00 PM
    val creatineReminderEnabled: Boolean = true,
    val creatineReminderTimeMinutes: Int = 9 * 60, // 9:00 AM
    val proteinReminderEnabled: Boolean = true,
    val proteinReminderTimeMinutes: Int = 19 * 60 // 7:00 PM
)

class TrackerSettingsStore(private val context: Context) {
    private object Keys {
        val WATER_GOAL = intPreferencesKey("water_goal_ml")
        val PUSH_GOAL = intPreferencesKey("push_goal_reps")
        val CREATINE = booleanPreferencesKey("creatine_enabled")
        val PROTEIN = booleanPreferencesKey("protein_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WATER_REMINDER = booleanPreferencesKey("water_reminder_enabled")
        val WATER_INTERVAL = intPreferencesKey("water_reminder_interval")
        val PUSHUP_REMINDER = booleanPreferencesKey("pushup_reminder_enabled")
        val PUSHUP_TIME = intPreferencesKey("pushup_reminder_time")
        val CREATINE_REMINDER = booleanPreferencesKey("creatine_reminder_enabled")
        val CREATINE_TIME = intPreferencesKey("creatine_reminder_time")
        val PROTEIN_REMINDER = booleanPreferencesKey("protein_reminder_enabled")
        val PROTEIN_TIME = intPreferencesKey("protein_reminder_time")
    }

    val settings: Flow<TrackerSettings> = context.trackerDataStore.data.map { p ->
        TrackerSettings(
            waterGoalMl = p[Keys.WATER_GOAL] ?: 2500,
            pushUpGoalReps = p[Keys.PUSH_GOAL] ?: 100,
            creatineEnabled = p[Keys.CREATINE] ?: true,
            proteinEnabled = p[Keys.PROTEIN] ?: true,
            themeMode = p[Keys.THEME_MODE] ?: "SYSTEM",
            waterReminderEnabled = p[Keys.WATER_REMINDER] ?: true,
            waterReminderIntervalHours = p[Keys.WATER_INTERVAL] ?: 2,
            pushUpReminderEnabled = p[Keys.PUSHUP_REMINDER] ?: true,
            pushUpReminderTimeMinutes = p[Keys.PUSHUP_TIME] ?: (18 * 60),
            creatineReminderEnabled = p[Keys.CREATINE_REMINDER] ?: true,
            creatineReminderTimeMinutes = p[Keys.CREATINE_TIME] ?: (9 * 60),
            proteinReminderEnabled = p[Keys.PROTEIN_REMINDER] ?: true,
            proteinReminderTimeMinutes = p[Keys.PROTEIN_TIME] ?: (19 * 60)
        )
    }

    suspend fun save(next: TrackerSettings) {
        require(next.waterGoalMl in 1..20000 && next.pushUpGoalReps in 1..10000) {
            "Goals out of valid range"
        }
        context.trackerDataStore.edit { p ->
            p[Keys.WATER_GOAL] = next.waterGoalMl
            p[Keys.PUSH_GOAL] = next.pushUpGoalReps
            p[Keys.CREATINE] = next.creatineEnabled
            p[Keys.PROTEIN] = next.proteinEnabled
            p[Keys.THEME_MODE] = next.themeMode
            p[Keys.WATER_REMINDER] = next.waterReminderEnabled
            p[Keys.WATER_INTERVAL] = next.waterReminderIntervalHours
            p[Keys.PUSHUP_REMINDER] = next.pushUpReminderEnabled
            p[Keys.PUSHUP_TIME] = next.pushUpReminderTimeMinutes
            p[Keys.CREATINE_REMINDER] = next.creatineReminderEnabled
            p[Keys.CREATINE_TIME] = next.creatineReminderTimeMinutes
            p[Keys.PROTEIN_REMINDER] = next.proteinReminderEnabled
            p[Keys.PROTEIN_TIME] = next.proteinReminderTimeMinutes
        }
    }
}
